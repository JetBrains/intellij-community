// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.core.CoreBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.ImmutableCharSequence;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.*;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.reference.SoftReference.dereference;

public final class DocumentImpl extends UserDataHolderBase implements DocumentEx {
  private static final Logger LOG = Logger.getInstance(DocumentImpl.class);
  private static final int STRIP_TRAILING_SPACES_BULK_MODE_LINES_LIMIT = 1000;
  private static final List<RangeMarker> GUARDED_IN_PROGRESS = new ArrayList<>(0);

  private final LockFreeCOWSortedArray<DocumentListener> myDocumentListeners =
    new LockFreeCOWSortedArray<>(PrioritizedDocumentListener.COMPARATOR, DocumentListener.ARRAY_FACTORY);
  private final RangeMarkerTree<RangeMarkerEx> myRangeMarkers = new RangeMarkerTree<>(this);
  private final RangeMarkerTree<RangeMarkerEx> myPersistentRangeMarkers = new PersistentRangeMarkerTree(this);
  private final AtomicReference<List<RangeMarker>> myGuardedBlocks = new AtomicReference<>();
  private ReadonlyFragmentModificationHandler myReadonlyFragmentModificationHandler;

  private final Object myLineSetLock = ObjectUtils.sentinel("line set lock");
  private volatile LineSet myLineSet;
  private volatile ImmutableCharSequence myText;
  private volatile SoftReference<String> myTextString;
  private volatile FrozenDocument myFrozen;

  private boolean myIsReadOnly;
  private volatile boolean isStripTrailingSpacesEnabled = true;
  private volatile long myModificationStamp;
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

  private final List<EditReadOnlyListener> myReadOnlyListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private int myCheckGuardedBlocks;
  private boolean myGuardsSuppressed;
  private boolean myEventsHandling;
  private final boolean myAssertThreading;
  private volatile boolean myDoingBulkUpdate;
  private volatile Throwable myBulkUpdateEnteringTrace;
  private boolean myUpdatingBulkModeStatus;
  private volatile boolean myAcceptSlashR;
  private boolean myChangeInProgress;
  private volatile int myBufferSize;
  private final CharSequence myMutableCharSequence = new CharSequence() {
    @Override
    public int length() {
      return myText.length();
    }

    @Override
    public char charAt(int index) {
      return myText.charAt(index);
    }

    @Override
    public @NotNull CharSequence subSequence(int start, int end) {
      return myText.subSequence(start, end);
    }

    @Override
    public @NotNull String toString() {
      return doGetText();
    }
  };
  private final AtomicInteger sequence = new AtomicInteger();

  public DocumentImpl(@NotNull String text) {
    this(text, false);
  }

  public DocumentImpl(@NotNull CharSequence chars) {
    this(chars, false);
  }

  /**
   * NOTE: if the client sets forUseInNonAWTThread to "true", then it's their responsibility to control the document and its listeners.
   * The noticeable peculiarity of DocumentImpl behavior in this mode is that it won't suppress ProcessCanceledException
   * thrown from listeners during "changedUpdate" event, so the exceptions will be rethrown and the remaining listeners WON'T be notified.
   */
  public DocumentImpl(@NotNull CharSequence chars, boolean forUseInNonAWTThread) {
    this(chars, false, forUseInNonAWTThread);
  }

  public DocumentImpl(@NotNull CharSequence chars, boolean acceptSlashR, boolean forUseInNonAWTThread) {
    setAcceptSlashR(acceptSlashR);
    assertValidSeparators(chars);
    myText = CharArrayUtil.createImmutableCharSequence(chars);
    setCyclicBufferSize(0);
    setModificationStamp(LocalTimeCounter.currentTime());
    myAssertThreading = !forUseInNonAWTThread;
  }

  @ApiStatus.Internal
  public static final Key<Boolean> IGNORE_RANGE_GUARDS_ON_FULL_UPDATE = Key.create("IGNORE_RANGE_GUARDS_ON_FULL_UPDATE");

  static final Key<Reference<RangeMarkerTree<RangeMarkerEx>>> RANGE_MARKERS_KEY = Key.create("RANGE_MARKERS_KEY");
  static final Key<Reference<RangeMarkerTree<RangeMarkerEx>>> PERSISTENT_RANGE_MARKERS_KEY = Key.create("PERSISTENT_RANGE_MARKERS_KEY");
  @ApiStatus.Internal
  public void documentCreatedFrom(@NotNull VirtualFile f, int tabSize) {
    processQueue();
    getSaveRMTree(f, RANGE_MARKERS_KEY, myRangeMarkers, tabSize);
    getSaveRMTree(f, PERSISTENT_RANGE_MARKERS_KEY, myPersistentRangeMarkers, tabSize);
  }

  // are some range markers retained by strong references?
  public static boolean areRangeMarkersRetainedFor(@NotNull VirtualFile f) {
    processQueue();
    // if a marker is retained, then so is its node and the whole tree
    // (ignore the race when marker is gc-ed right after this call - it's harmless)
    return dereference(f.getUserData(RANGE_MARKERS_KEY)) != null
           || dereference(f.getUserData(PERSISTENT_RANGE_MARKERS_KEY)) != null;
  }

  private void getSaveRMTree(@NotNull VirtualFile f,
                             @NotNull Key<Reference<RangeMarkerTree<RangeMarkerEx>>> key,
                             @NotNull RangeMarkerTree<RangeMarkerEx> tree,
                             int tabSize) {
    RMTreeReference freshRef = new RMTreeReference(tree, f);
    Reference<RangeMarkerTree<RangeMarkerEx>> oldRef;
    do {
      oldRef = f.getUserData(key);
    }
    while (!f.replace(key, oldRef, freshRef));
    RangeMarkerTree<RangeMarkerEx> oldTree = dereference(oldRef);

    if (oldTree == null) {
      // no tree was saved in virtual file before (happens when a new document is created).
      // or the old tree got gc-ed, because no reachable markers retaining it are left alive. good riddance.
      return;
    }

    // Some old tree was saved in the virtual file. Have to transfer markers from there.
    oldTree.copyRangeMarkersTo(this, tabSize);
  }

  // track GC of RangeMarkerTree: means no one is interested in range markers for this file anymore
  private static final ReferenceQueue<RangeMarkerTree<RangeMarkerEx>> rmTreeQueue = new ReferenceQueue<>();
  private static class RMTreeReference extends WeakReference<RangeMarkerTree<RangeMarkerEx>> {
    private final @NotNull VirtualFile virtualFile;

    RMTreeReference(@NotNull RangeMarkerTree<RangeMarkerEx> referent, @NotNull VirtualFile virtualFile) {
      super(referent, rmTreeQueue);
      this.virtualFile = virtualFile;
    }
  }

  @ApiStatus.Internal
  public static void processQueue() {
    RMTreeReference ref;
    while ((ref = (RMTreeReference)rmTreeQueue.poll()) != null) {
      ref.virtualFile.replace(RANGE_MARKERS_KEY, ref, null);
      ref.virtualFile.replace(PERSISTENT_RANGE_MARKERS_KEY, ref, null);
    }
  }

  /**
   * makes range marker without creating the document (which could be expensive)
   */
  static @NotNull RangeMarker createRangeMarkerForVirtualFile(@NotNull VirtualFile file,
                                                     int offset,
                                                     int startLine,
                                                     int startCol,
                                                     int endLine,
                                                     int endCol,
                                                     boolean persistent) {
    int estimatedLength = RangeMarkerImpl.estimateDocumentLength(file);
    offset = Math.min(offset, estimatedLength);
    RangeMarkerImpl marker = persistent
                             ? new PersistentRangeMarker(file, offset, offset, startLine, startCol, endLine, endCol, estimatedLength, false)
                             : new RangeMarkerImpl(file, offset, offset, estimatedLength, false);
    Key<Reference<RangeMarkerTree<RangeMarkerEx>>> key = persistent ? PERSISTENT_RANGE_MARKERS_KEY : RANGE_MARKERS_KEY;
    RangeMarkerTree<RangeMarkerEx> tree;
    while (true) {
      Reference<RangeMarkerTree<RangeMarkerEx>> oldRef = file.getUserData(key);
      tree = dereference(oldRef);
      if (tree != null) break;
      tree = new RangeMarkerTree<>();
      RMTreeReference reference = new RMTreeReference(tree, file);
      if (file.replace(key, oldRef, reference)) break;
    }
    tree.addInterval(marker, offset, offset, false, false, false, 0);

    return marker;

  }
  public boolean setAcceptSlashR(boolean accept) {
    try {
      return myAcceptSlashR;
    }
    finally {
      myAcceptSlashR = accept;
    }
  }

  public boolean acceptsSlashR() {
    return myAcceptSlashR;
  }

  private LineSet getLineSet() {
    LineSet lineSet = myLineSet;
    if (lineSet == null) {
      synchronized (myLineSetLock) {
        lineSet = myLineSet;
        if (lineSet == null) {
          lineSet = LineSet.createLineSet(myText);
          myLineSet = lineSet;
        }
      }
    }

    return lineSet;
  }

  @Override
  public void setStripTrailingSpacesEnabled(boolean isEnabled) {
    isStripTrailingSpacesEnabled = isEnabled;
  }

  @TestOnly
  public boolean stripTrailingSpaces(Project project) {
    return stripTrailingSpaces(project, false);
  }

  @TestOnly
  public boolean stripTrailingSpaces(Project project, boolean inChangedLinesOnly) {
    return stripTrailingSpaces(project, inChangedLinesOnly, null);
  }

  @Override
  public boolean isLineModified(int line) {
    LineSet lineSet = myLineSet;
    return lineSet != null && lineSet.isModified(line);
  }

  /**
   * @return true if stripping was completed successfully, false if the document prevented stripping by e.g., caret(s) being in the way
   */
  boolean stripTrailingSpaces(@Nullable Project project, boolean inChangedLinesOnly, int @Nullable [] caretOffsets) {
    if (!isStripTrailingSpacesEnabled) {
      return true;
    }
    List<StripTrailingSpacesFilter> filters = new ArrayList<>();
    StripTrailingSpacesFilter specialFilter = null;
    for (StripTrailingSpacesFilterFactory filterFactory : StripTrailingSpacesFilterFactory.EXTENSION_POINT.getExtensionList()) {
      StripTrailingSpacesFilter filter = filterFactory.createFilter(project, this);
      if (specialFilter == null &&
          (filter == StripTrailingSpacesFilter.NOT_ALLOWED || filter == StripTrailingSpacesFilter.POSTPONED)) {
        specialFilter = filter;
      }
      else if (filter == StripTrailingSpacesFilter.ENFORCED_REMOVAL) {
        specialFilter = null;
        filters.clear();
        break;
      }
      else {
        filters.add(filter);
      }
    }

    if (specialFilter != null) {
      return specialFilter == StripTrailingSpacesFilter.NOT_ALLOWED;
    }

    Int2IntMap caretPositions = null;
    if (caretOffsets != null) {
      caretPositions = new Int2IntOpenHashMap(caretOffsets.length);
      for (int caretOffset : caretOffsets) {
        int line = getLineNumber(caretOffset);
        // need to remember only maximum caret offset on a line
        caretPositions.put(line, Math.max(caretOffset, caretPositions.get(line)));
      }
    }

    LineSet lineSet = getLineSet();
    int lineCount = getLineCount();
    int[] targetOffsets = new int[lineCount * 2];
    int targetOffsetPos = 0;
    boolean markAsNeedsStrippingLater = false;
    CharSequence text = myText;
    for (int line = 0; line < lineCount; line++) {
      int maxSpacesToLeave = getMaxSpacesToLeave(line, filters);
      if (inChangedLinesOnly && !lineSet.isModified(line) || maxSpacesToLeave < 0) continue;

      int whiteSpaceStart = -1;
      int lineEnd = lineSet.getLineEnd(line) - lineSet.getSeparatorLength(line);
      int lineStart = lineSet.getLineStart(line);
      for (int offset = lineEnd - 1; offset >= lineStart; offset--) {
        char c = text.charAt(offset);
        if (c != ' ' && c != '\t') {
          break;
        }
        whiteSpaceStart = offset;
      }
      if (whiteSpaceStart == -1) continue;

      if (caretPositions != null) {
        int caretPosition = caretPositions.get(line);
        if (whiteSpaceStart < caretPosition) {
          markAsNeedsStrippingLater = true;
          continue;
        }
      }

      int finalStart = whiteSpaceStart + maxSpacesToLeave;
      if (finalStart < lineEnd) {
        targetOffsets[targetOffsetPos++] = finalStart;
        targetOffsets[targetOffsetPos++] = lineEnd;
      }
    }
    int finalTargetOffsetPos = targetOffsetPos;
    // Document must be unblocked by now. If not, some Save handler attempted to modify PSI
    // which should have been caught by assertion in com.intellij.pom.core.impl.PomModelImpl.runTransaction
    DocumentUtil.writeInRunUndoTransparentAction(new DocumentRunnable(this, project) {
      @Override
      public void run() {
        DocumentUtil.executeInBulk(DocumentImpl.this, finalTargetOffsetPos > STRIP_TRAILING_SPACES_BULK_MODE_LINES_LIMIT * 2, () -> {
          int pos = finalTargetOffsetPos;
          while (pos > 0) {
            int endOffset = targetOffsets[--pos];
            int startOffset = targetOffsets[--pos];
            deleteString(startOffset, endOffset);
          }
        });
      }
    });
    return markAsNeedsStrippingLater;
  }

  private static int getMaxSpacesToLeave(int line, @NotNull List<? extends StripTrailingSpacesFilter> filters) {
    for (StripTrailingSpacesFilter filter :  filters) {
      if (filter instanceof SmartStripTrailingSpacesFilter) {
        return ((SmartStripTrailingSpacesFilter)filter).getTrailingSpacesToLeave(line);
      }
      else  if (!filter.isStripSpacesAllowedForLine(line)) {
        return -1;
      }
    }
    return 0;
  }

  @Override
  public void setReadOnly(boolean isReadOnly) {
    if (myIsReadOnly != isReadOnly) {
      myIsReadOnly = isReadOnly;
      myPropertyChangeSupport.firePropertyChange(PROP_WRITABLE, !isReadOnly, isReadOnly);
    }
  }

  ReadonlyFragmentModificationHandler getReadonlyFragmentModificationHandler() {
    return myReadonlyFragmentModificationHandler;
  }

  void setReadonlyFragmentModificationHandler(ReadonlyFragmentModificationHandler readonlyFragmentModificationHandler) {
    myReadonlyFragmentModificationHandler = readonlyFragmentModificationHandler;
  }

  @Override
  public boolean isWritable() {
    if (myIsReadOnly) {
      return false;
    }

    for (DocumentWriteAccessGuard guard : DocumentWriteAccessGuard.EP_NAME.getExtensionList()) {
      if (!guard.isWritable(this).isSuccess()) {
        return false;
      }
    }

    return true;
  }

  private RangeMarkerTree<RangeMarkerEx> treeFor(@NotNull RangeMarkerEx rangeMarker) {
    return rangeMarker instanceof PersistentRangeMarker ? myPersistentRangeMarkers : myRangeMarkers;
  }
  @Override
  public boolean removeRangeMarker(@NotNull RangeMarkerEx rangeMarker) {
    return treeFor(rangeMarker).removeInterval(rangeMarker);
  }

  @Override
  public void registerRangeMarker(@NotNull RangeMarkerEx rangeMarker,
                                  int start,
                                  int end,
                                  boolean greedyToLeft,
                                  boolean greedyToRight,
                                  int layer) {
    treeFor(rangeMarker).addInterval(rangeMarker, start, end, greedyToLeft, greedyToRight, false, layer);
  }

  @TestOnly
  int getRangeMarkersSize() {
    return myRangeMarkers.size() + myPersistentRangeMarkers.size();
  }

  @TestOnly
  int getRangeMarkersNodeSize() {
    return myRangeMarkers.nodeSize()+myPersistentRangeMarkers.nodeSize();
  }

  @Override
  public @NotNull RangeMarker createGuardedBlock(int startOffset, int endOffset) {
    LOG.assertTrue(startOffset <= endOffset, "Should be startOffset <= endOffset");
    GuardedBlock block = new GuardedBlock(this, startOffset, endOffset);
    myGuardedBlocks.set(null);
    return block;
  }

  @Override
  public void removeGuardedBlock(@NotNull RangeMarker block) {
    if (!GuardedBlock.isGuarded(block)) {
      throw new IllegalArgumentException("range markers is not a guarded block");
    }
    block.dispose();
    myGuardedBlocks.set(null);
  }

  @Override
  public @NotNull List<RangeMarker> getGuardedBlocks() {
    List<RangeMarker> cachedBlocks = myGuardedBlocks.get();
    if (cachedBlocks != null && cachedBlocks != GUARDED_IN_PROGRESS) {
      return cachedBlocks;
    }
    if (myGuardedBlocks.compareAndSet(null, GUARDED_IN_PROGRESS)) {
      List<RangeMarker> blocks = collectGuardedBlocks();
      if (!myGuardedBlocks.compareAndSet(GUARDED_IN_PROGRESS, blocks)) {
        // another thread created or removed a block, force recalculation
        myGuardedBlocks.set(null);
      }
      return blocks;
    }
    // another thread is already collecting the result, return without commiting
    return collectGuardedBlocks();
  }

  private @NotNull List<RangeMarker> collectGuardedBlocks() {
    List<RangeMarker> blocks = new ArrayList<>();
    myPersistentRangeMarkers.processAll(GuardedBlock.processor(block -> {
      blocks.add(block);
      return true;
    }));
    // prevent the users from being misled that modifying this list affects actual guarded blocks
    return Collections.unmodifiableList(blocks);
  }

  @Override
  public RangeMarker getOffsetGuard(int offset) {
    Ref<RangeMarker> blockRef = new Ref<>();
    myPersistentRangeMarkers.processContaining(offset, GuardedBlock.processor(block -> {
      blockRef.set(block);
      return false;
    }));
    return blockRef.get();
  }

  @Override
  public RangeMarker getRangeGuard(int start, int end) {
    Ref<RangeMarker> blockRef = new Ref<>();
    myPersistentRangeMarkers.processOverlappingWith(start, end, GuardedBlock.processor(block -> {
      if (rangesIntersect(start, end, true, true,
                          block.getStartOffset(), block.getEndOffset(),
                          block.isGreedyToLeft(), block.isGreedyToRight())) {
        blockRef.set(block);
        return false;
      }
      return true;
    }));
    return blockRef.get();
  }

  @Override
  public void startGuardedBlockChecking() {
    myCheckGuardedBlocks++;
  }

  @Override
  public void stopGuardedBlockChecking() {
    LOG.assertTrue(myCheckGuardedBlocks > 0, "Unpaired start/stopGuardedBlockChecking");
    myCheckGuardedBlocks--;
  }

  private static boolean rangesIntersect(int start0, int end0, boolean start0Inclusive, boolean end0Inclusive,
                                         int start1, int end1, boolean start1Inclusive, boolean end1Inclusive) {
    if (start0 > start1 || start0 == start1 && !start0Inclusive) {
      if (end1 == start0) return start0Inclusive && end1Inclusive;
      return end1 > start0;
    }
    if (end0 == start1) return start1Inclusive && end0Inclusive;
    return end0 > start1;
  }

  @Override
  public @NotNull RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
    return surviveOnExternalChange
           ? new PersistentRangeMarker(this, startOffset, endOffset, true)
           : new RangeMarkerImpl(this, startOffset, endOffset, true, false);
  }

  @Override
  public long getModificationStamp() {
    return myModificationStamp;
  }

  @Override
  public void setModificationStamp(long modificationStamp) {
    myModificationStamp = modificationStamp;
    myFrozen = null;
  }

  @Override
  public void replaceText(@NotNull CharSequence chars, long newModificationStamp) {
    replaceString(0, getTextLength(), 0, chars, newModificationStamp, true); //TODO: optimization!!!
    clearLineModificationFlags();
  }

  @Override
  public void insertString(int offset, @NotNull CharSequence s) {
    if (offset < 0) throw new IndexOutOfBoundsException("Wrong offset: " + offset);
    if (offset > getTextLength()) {
      throw new IndexOutOfBoundsException("Wrong offset: " + offset + "; documentLength: " + getTextLength());
    }
    assertWriteAccess();
    assertValidSeparators(s);

    if (s.length() == 0) return;

    RangeMarker marker = getRangeGuard(offset, offset);
    if (marker != null) {
      throwGuardedFragment(marker, offset, "", s);
    }

    ImmutableCharSequence newText = myText.insert(offset, s);
    ImmutableCharSequence newString = newText.subtext(offset, offset + s.length());
    updateText(newText, offset, "", newString, false, LocalTimeCounter.currentTime(),
               offset, 0, offset);
    trimToSize();
  }

  private void trimToSize() {
    if (myBufferSize != 0 && getTextLength() > myBufferSize) {
      deleteString(0, getTextLength() - myBufferSize);
    }
  }

  @Override
  public void deleteString(int startOffset, int endOffset) {
    assertBounds(startOffset, endOffset);

    assertWriteAccess();
    if (startOffset == endOffset) return;

    RangeMarker marker = getRangeGuard(startOffset, endOffset);
    if (marker != null) {
      throwGuardedFragment(marker, startOffset, myText.subSequence(startOffset, endOffset), "");
    }

    ImmutableCharSequence newText = myText.delete(startOffset, endOffset);
    ImmutableCharSequence oldString = myText.subtext(startOffset, endOffset);
    updateText(newText, startOffset, oldString, "", false, LocalTimeCounter.currentTime(),
               startOffset, endOffset - startOffset, startOffset);
  }

  @Override
  public void moveText(int srcStart, int srcEnd, int dstOffset) {
    assertBounds(srcStart, srcEnd);
    if (dstOffset == srcStart || dstOffset == srcEnd) return;
    ProperTextRange srcRange = new ProperTextRange(srcStart, srcEnd);
    assert !srcRange.containsOffset(dstOffset) : "Can't perform text move from range [" + srcStart + "; " + srcEnd + ") to offset " + dstOffset;

    String replacement = getCharsSequence().subSequence(srcStart, srcEnd).toString();
    int shift = dstOffset < srcStart ? srcEnd - srcStart : 0;

    // a pair of insert/remove modifications
    replaceString(dstOffset, dstOffset, srcStart + shift, replacement, LocalTimeCounter.currentTime(), false);
    replaceString(srcStart + shift, srcEnd + shift, dstOffset, "", LocalTimeCounter.currentTime(), false);
  }

  @Override
  public void replaceString(int startOffset, int endOffset, @NotNull CharSequence s) {
    replaceString(startOffset, endOffset, startOffset, s, LocalTimeCounter.currentTime(), false);
  }

  @ApiStatus.Internal
  public void replaceString(int startOffset, int endOffset, int moveOffset, @NotNull CharSequence s,
                            long newModificationStamp, boolean wholeTextReplaced) {
    assertBounds(startOffset, endOffset);

    assertWriteAccess();
    assertValidSeparators(s);

    if (moveOffset != startOffset && startOffset != endOffset && s.length() != 0) {
      throw new IllegalArgumentException(
        "moveOffset != startOffset for a modification which is neither an insert nor deletion." +
        " startOffset: " + startOffset + "; endOffset: " + endOffset + ";" + "; moveOffset: " + moveOffset + ";");
    }

    int initialStartOffset = startOffset;
    int initialOldLength = endOffset - startOffset;

    int newStringLength = s.length();
    CharSequence chars = myText;
    int newStartInString = 0;
    while (newStartInString < newStringLength &&
           startOffset < endOffset &&
           s.charAt(newStartInString) == chars.charAt(startOffset)) {
      startOffset++;
      newStartInString++;
    }
    if (newStartInString == newStringLength &&
        startOffset == endOffset &&
        !wholeTextReplaced) {
      return;
    }

    int newEndInString = newStringLength;
    while (endOffset > startOffset &&
           newEndInString > newStartInString &&
           s.charAt(newEndInString - 1) == chars.charAt(endOffset - 1)) {
      newEndInString--;
      endOffset--;
    }

    if (startOffset == 0 && endOffset == getTextLength()) {
      wholeTextReplaced = true;
    }

    CharSequence changedPart = s.subSequence(newStartInString, newEndInString);
    CharSequence sToDelete = myText.subtext(startOffset, endOffset);
    boolean isForceIgnoreGuardsOnFullUpdate = getUserData(IGNORE_RANGE_GUARDS_ON_FULL_UPDATE) == Boolean.TRUE && wholeTextReplaced;
    if (!isForceIgnoreGuardsOnFullUpdate) {
      RangeMarker guard = getRangeGuard(startOffset, endOffset);
      if (guard != null) {
        throwGuardedFragment(guard, startOffset, sToDelete, changedPart);
      }
    }


    ImmutableCharSequence newText;
    if (wholeTextReplaced && s instanceof ImmutableCharSequence) {
      newText = (ImmutableCharSequence)s;
    }
    else {
      newText = myText.replace(startOffset, endOffset, changedPart);
      changedPart = newText.subtext(startOffset, startOffset + changedPart.length());
    }
    boolean wasOptimized = initialStartOffset != startOffset || endOffset - startOffset != initialOldLength;
    updateText(newText, startOffset, sToDelete, changedPart, wholeTextReplaced, newModificationStamp,
               initialStartOffset, initialOldLength, wasOptimized ? startOffset : moveOffset);
    trimToSize();
  }

  private void assertBounds(int startOffset, int endOffset) {
    if (startOffset < 0 || startOffset > getTextLength()) {
      throw new IndexOutOfBoundsException("Wrong startOffset: " + startOffset + "; documentLength: " + getTextLength());
    }
    if (endOffset < 0 || endOffset > getTextLength()) {
      throw new IndexOutOfBoundsException("Wrong endOffset: " + endOffset + "; documentLength: " + getTextLength());
    }
    if (endOffset < startOffset) {
      throw new IllegalArgumentException("endOffset < startOffset: " + endOffset + " < " + startOffset + "; documentLength: " + getTextLength());
    }
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  public boolean isWriteThreadOnly() {
    return myAssertThreading;
  }

  private void assertWriteAccess() {
    if (myAssertThreading) {
      Application application = ApplicationManager.getApplication();
      if (application != null) {
        application.assertWriteAccessAllowed();
        VirtualFile file = FileDocumentManager.getInstance().getFile(this);
        if (file != null && file.isInLocalFileSystem()) {
          ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();
        }
      }
    }

    if (myIsReadOnly) {
      throw new ReadOnlyModificationException(this, CoreBundle.message("attempt.to.modify.read.only.document.error.message"));
    }

    for (DocumentWriteAccessGuard guard : DocumentWriteAccessGuard.EP_NAME.getExtensionList()) {
      DocumentWriteAccessGuard.Result result = guard.isWritable(this);
      if (!result.isSuccess()) {
        throw new ReadOnlyModificationException(
          this, String.format("%s: guardClass=%s, failureReason=%s",
                              CoreBundle.message("attempt.to.modify.read.only.document.error.message"),
                              guard.getClass().getName(), result.getFailureReason()));
      }
    }
  }

  private void assertValidSeparators(@NotNull CharSequence s) {
    if (myAcceptSlashR) return;
    StringUtil.assertValidSeparators(s);
  }

  /**
   * All document change actions follows the algorithm below:
   * <pre>
   * <ol>
   *   <li>
   *     All {@link #addDocumentListener(DocumentListener) registered listeners} are notified
   *     {@link DocumentListener#beforeDocumentChange(DocumentEvent) before the change};
   *   </li>
   *   <li>The change is performed </li>
   *   <li>
   *     All {@link #addDocumentListener(DocumentListener) registered listeners} are notified
   *     {@link DocumentListener#documentChanged(DocumentEvent) after the change};
   *   </li>
   * </ol>
   * </pre>
   * <p/>
   * There is a possible case that {@code 'before change'} notification produces new change. We have a problem then - imagine
   * that initial change was {@code 'replace particular range at document end'} and {@code 'nested change'} was to
   * {@code 'remove text at document end'}. That means that when initial change is actually performed, the document may be
   * not long enough to contain target range.
   * <p/>
   * Current method allows checking if document change is a {@code 'nested call'}.
   *
   * @throws IllegalStateException if this method is called during a {@code 'nested document modification'}
   */
  private void assertNotNestedModification() throws IllegalStateException {
    if (myChangeInProgress) {
      throw new IllegalStateException("Detected document modification from DocumentListener");
    }
  }

  private void throwGuardedFragment(@NotNull RangeMarker guard, int offset, @NotNull CharSequence oldString, @NotNull CharSequence newString) {
    if (myCheckGuardedBlocks > 0 && !myGuardsSuppressed) {
      DocumentEvent event = new DocumentEventImpl(this, offset, oldString, newString, myModificationStamp, false, offset, oldString.length(),
                                                  offset);
      throw new ReadOnlyFragmentModificationException(event, guard);
    }
  }

  @Override
  public void suppressGuardedExceptions() {
    myGuardsSuppressed = true;
  }

  @Override
  public void unSuppressGuardedExceptions() {
    myGuardsSuppressed = false;
  }

  @Override
  public boolean isInEventsHandling() {
    return myEventsHandling;
  }

  @Override
  public void clearLineModificationFlags() {
    myLineSet = getLineSet().clearModificationFlags();
    myFrozen = null;
  }

  public void clearLineModificationFlags(int startLine, int endLine) {
    myLineSet = getLineSet().clearModificationFlags(startLine, endLine);
    myFrozen = null;
  }

  void clearLineModificationFlagsExcept(int @NotNull [] caretLines) {
    IntList modifiedLines = new IntArrayList(caretLines.length);
    LineSet lineSet = getLineSet();
    for (int line : caretLines) {
      if (line >= 0 && line < lineSet.getLineCount() && lineSet.isModified(line)) {
        modifiedLines.add(line);
      }
    }
    lineSet = lineSet.clearModificationFlags();
    lineSet = lineSet.setModified(modifiedLines);
    myLineSet = lineSet;
    myFrozen = null;
  }

  private void updateText(@NotNull ImmutableCharSequence newText,
                          int offset,
                          @NotNull CharSequence oldString,
                          @NotNull CharSequence newString,
                          boolean wholeTextReplaced,
                          long newModificationStamp,
                          int initialStartOffset,
                          int initialOldLength,
                          int moveOffset) {
    if (LOG.isTraceEnabled()) {
      LOG.trace("updating document " + this + ".\nNext string:'" + newString + "'\nOld string:'" + oldString + "'");
    }

    assert moveOffset >= 0 && moveOffset <= getTextLength() : "Invalid moveOffset: " + moveOffset;
    assertNotNestedModification();
    myChangeInProgress = true;
    DelayedExceptions exceptions = new DelayedExceptions();
    try {
      DocumentEvent event = new DocumentEventImpl(this, offset, oldString, newString, myModificationStamp, wholeTextReplaced,
                                                  initialStartOffset, initialOldLength, moveOffset);
      beforeChangedUpdate(event, exceptions);
      myTextString = null;
      ImmutableCharSequence prevText = myText;
      myText = newText;
      // increment sequence before firing events, so that the modification sequence on commit will match this sequence now
      sequence.incrementAndGet();
      changedUpdate(event, newModificationStamp, prevText, exceptions);
    }
    finally {
      myChangeInProgress = false;
      exceptions.rethrowPCE();
    }
  }

  private final class DelayedExceptions {
    private Throwable myException;

    void register(@NotNull Throwable e) {
      if (myException == null) {
        myException = e;
      }
      else {
        myException.addSuppressed(e);
      }

      if (!(e instanceof ProcessCanceledException)) {
        LOG.error(e);
      }
      else if (myAssertThreading) {
        LOG.error("ProcessCanceledException must not be thrown from document listeners for real document", new Throwable(e));
      }
    }

    void rethrowPCE() {
      if (myException instanceof ProcessCanceledException) {
        // the case of some wise inspection modifying a non-physical document during highlighting to be interrupted
        throw (ProcessCanceledException)myException;
      }
    }
  }

  @Override
  public int getModificationSequence() {
    return sequence.get();
  }

  private void beforeChangedUpdate(DocumentEvent event, DelayedExceptions exceptions) {
    Application app = ApplicationManager.getApplication();
    if (app != null) {
      FileDocumentManager manager = FileDocumentManager.getInstance();
      VirtualFile file = manager.getFile(this);
      if (file != null && !file.isValid()) {
        LOG.error("File of this document has been deleted: "+file);
      }
    }
    assertInsideCommand();

    getLineSet(); // initialize line set to track changed lines

    if (!ShutDownTracker.isShutdownStarted()) {
      DocumentListener[] listeners = getListeners();
      ProgressManager.getInstance().executeNonCancelableSection(() -> {
        for (int i = listeners.length - 1; i >= 0; i--) {
          try {
            listeners[i].beforeDocumentChange(event);
          }
          catch (Throwable e) {
            exceptions.register(e);
          }
        }
      });
    }

    myEventsHandling = true;
  }

  private void assertInsideCommand() {
    if (!myAssertThreading) return;
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    if (!commandProcessor.isUndoTransparentActionInProgress() &&
        commandProcessor.getCurrentCommand() == null) {
      throw new IncorrectOperationException("Must not change document outside command or undo-transparent action. See com.intellij.openapi.command.WriteCommandAction or com.intellij.openapi.command.CommandProcessor");
    }
  }

  private void changedUpdate(@NotNull DocumentEvent event, long newModificationStamp, @NotNull CharSequence prevText, DelayedExceptions exceptions) {
    try {
      if (LOG.isTraceEnabled()) LOG.trace(new Throwable(event.toString()));
      else if (LOG.isDebugEnabled()) LOG.debug(event.toString());

      assert event.getOldFragment().length() ==  event.getOldLength() : "event.getOldFragment().length() = " + event.getOldFragment().length()+"; event.getOldLength() = " + event.getOldLength();
      assert event.getNewFragment().length() ==  event.getNewLength() : "event.getNewFragment().length() = " + event.getNewFragment().length()+"; event.getNewLength() = " + event.getNewLength();
      assert prevText.length() + event.getNewLength() - event.getOldLength() == getTextLength() : "prevText.length() = " + prevText.length()+ "; event.getNewLength() = " + event.getNewLength()+ "; event.getOldLength() = " + event.getOldLength()+ "; getTextLength() = " + getTextLength();

      myLineSet = getLineSet().update(prevText, event.getOffset(), event.getOffset() + event.getOldLength(), event.getNewFragment(), event.isWholeTextReplaced());
      assert getTextLength() == myLineSet.getLength() : "getTextLength() = " + getTextLength()+ "; myLineSet.getLength() = " + myLineSet.getLength();

      myFrozen = null;
      setModificationStamp(newModificationStamp);

      if (!ShutDownTracker.isShutdownStarted()) {
        DocumentListener[] listeners = getListeners();
        ProgressManager.getInstance().executeNonCancelableSection(() -> {
          for (DocumentListener listener : listeners) {
            try {
              listener.documentChanged(event);
            }
            catch (Throwable e) {
              exceptions.register(e);
            }
          }
        });
      }
    }
    finally {
      myEventsHandling = false;
    }
  }

  @Override
  public @NotNull String getText() {
    return ReadAction.compute(this::doGetText);
  }

  private @NotNull String doGetText() {
    String s = dereference(myTextString);
    if (s == null) {
      myTextString = new SoftReference<>(s = myText.toString());
    }
    return s;
  }

  @Override
  public @NotNull String getText(@NotNull TextRange range) {
    return ReadAction
      .compute(() -> myText.subSequence(range.getStartOffset(), range.getEndOffset()).toString());
  }

  @Override
  public int getTextLength() {
    return myText.length();
  }

  @Override
  public @NotNull CharSequence getCharsSequence() {
    return myMutableCharSequence;
  }

  @Override
  public @NotNull CharSequence getImmutableCharSequence() {
    return myText;
  }

  @Override
  public void addDocumentListener(@NotNull DocumentListener listener) {
    if (ArrayUtil.contains(listener, getListeners())) {
      LOG.error("Already registered: " + listener);
    }
    myDocumentListeners.add(listener);
  }

  @Override
  public void addDocumentListener(@NotNull DocumentListener listener, @NotNull Disposable parentDisposable) {
    addDocumentListener(listener);
    Disposer.register(parentDisposable, new DocumentListenerDisposable(myDocumentListeners, listener));
  }

  // this contortion is for avoiding document leak when the listener is leaked
  private static class DocumentListenerDisposable implements Disposable {
    private final @NotNull LockFreeCOWSortedArray<? super DocumentListener> myList;
    private final @NotNull DocumentListener myListener;

    DocumentListenerDisposable(@NotNull LockFreeCOWSortedArray<? super DocumentListener> list, @NotNull DocumentListener listener) {
      myList = list;
      myListener = listener;
    }

    @Override
    public void dispose() {
      myList.remove(myListener);
    }
  }

  @Override
  public void removeDocumentListener(@NotNull DocumentListener listener) {
    boolean success = myDocumentListeners.remove(listener);
    if (!success) {
      LOG.error("Can't remove document listener (" + listener + "). Registered listeners: " + Arrays.toString(getListeners()));
    }
  }

  @Override
  public int getLineNumber(int offset) {
    return getLineSet().findLineIndex(offset);
  }

  @Override
  public @NotNull LineIterator createLineIterator() {
    return getLineSet().createIterator();
  }

  @Override
  public int getLineStartOffset(int line) {
    if (line == 0) return 0; // otherwise, it would crash for the zero-length document
    return getLineSet().getLineStart(line);
  }

  @Override
  public int getLineEndOffset(int line) {
    if (getTextLength() == 0 && line == 0) return 0;
    int result = getLineSet().getLineEnd(line) - getLineSeparatorLength(line);
    assert result >= 0;
    return result;
  }

  @Override
  public int getLineSeparatorLength(int line) {
    int separatorLength = getLineSet().getSeparatorLength(line);
    assert separatorLength >= 0;
    return separatorLength;
  }

  @Override
  public int getLineCount() {
    int lineCount = getLineSet().getLineCount();
    assert lineCount >= 0;
    return lineCount;
  }

  private DocumentListener @NotNull [] getListeners() {
    return myDocumentListeners.getArray();
  }

  @Override
  public void fireReadOnlyModificationAttempt() {
    for (EditReadOnlyListener listener : myReadOnlyListeners) {
      listener.readOnlyModificationAttempt(this);
    }
  }

  @Override
  public void addEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
    myReadOnlyListeners.add(listener);
  }

  @Override
  public void removeEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
    myReadOnlyListeners.remove(listener);
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  @Override
  public void setCyclicBufferSize(int bufferSize) {
    assert bufferSize >= 0 : bufferSize;
    myBufferSize = bufferSize;
  }

  @Override
  public void setText(@NotNull CharSequence text) {
    Runnable runnable = () -> replaceString(0, getTextLength(), 0, text, LocalTimeCounter.currentTime(), true);
    if (CommandProcessor.getInstance().isUndoTransparentActionInProgress() || !myAssertThreading) {
      runnable.run();
    }
    else {
      CommandProcessor.getInstance().executeCommand(null, runnable, "", DocCommandGroupId.noneGroupId(this));
    }

    clearLineModificationFlags();
  }

  @Override
  public boolean isInBulkUpdate() {
    return myDoingBulkUpdate;
  }

  @Override
  public void setInBulkUpdate(boolean value) {
    if (myAssertThreading) {
      ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    }
    if (myUpdatingBulkModeStatus) {
      throw new IllegalStateException("Detected bulk mode status update from DocumentBulkUpdateListener");
    }
    if (myDoingBulkUpdate == value) {
      return;
    }
    myUpdatingBulkModeStatus = true;
    try {
      if (value) {
        getPublisher().updateStarted(this);
        notifyListenersOnBulkModeStarting();
        myBulkUpdateEnteringTrace = new Throwable();
        myDoingBulkUpdate = true;
      }
      else {
        myDoingBulkUpdate = false;
        myBulkUpdateEnteringTrace = null;
        notifyListenersOnBulkModeFinished();
        getPublisher().updateFinished(this);
      }
    }
    finally {
      myUpdatingBulkModeStatus = false;
    }
  }

  private void notifyListenersOnBulkModeStarting() {
    DelayedExceptions exceptions = new DelayedExceptions();
    DocumentListener[] listeners = getListeners();
    for (int i = listeners.length - 1; i >= 0; i--) {
      try {
        listeners[i].bulkUpdateStarting(this);
      }
      catch (Throwable e) {
        exceptions.register(e);
      }
    }
    exceptions.rethrowPCE();
  }

  private void notifyListenersOnBulkModeFinished() {
    DelayedExceptions exceptions = new DelayedExceptions();
    DocumentListener[] listeners = getListeners();
    for (DocumentListener listener : listeners) {
      try {
        listener.bulkUpdateFinished(this);
      }
      catch (Throwable e) {
        exceptions.register(e);
      }
    }
    exceptions.rethrowPCE();
  }

  private static class DocumentBulkUpdateListenerHolder {
    private static final DocumentBulkUpdateListener ourBulkChangePublisher =
      ApplicationManager.getApplication().getMessageBus().syncPublisher(DocumentBulkUpdateListener.TOPIC);
  }

  private static @NotNull DocumentBulkUpdateListener getPublisher() {
    return DocumentBulkUpdateListenerHolder.ourBulkChangePublisher;
  }

  @Override
  public boolean processRangeMarkers(@NotNull Processor<? super RangeMarker> processor) {
    return processRangeMarkersOverlappingWith(0, getTextLength(), processor);
  }

  @Override
  public boolean processRangeMarkersOverlappingWith(int start, int end, @NotNull Processor<? super RangeMarker> processor) {
    TextRange interval = new ProperTextRange(start, end);
    MarkupIterator<RangeMarkerEx> iterator = IntervalTreeImpl
      .mergingOverlappingIterator(myRangeMarkers, interval, myPersistentRangeMarkers, interval, RangeMarker.BY_START_OFFSET);
    try {
      return ContainerUtil.process(iterator, processor);
    }
    finally {
      iterator.dispose();
    }
  }

  public @NotNull String dumpState() {
    @NonNls StringBuilder result = new StringBuilder();
    result.append("intervals:\n");
    int lineCount = getLineCount();
    for (int line = 0; line < lineCount; line++) {
      result.append(line).append(": ").append(getLineStartOffset(line)).append("-").append(getLineEndOffset(line)).append(", ");
    }
    if (lineCount > 0) {
      result.setLength(result.length() - 2);
    }
    return result.toString();
  }

  @Override
  public String toString() {
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(this);
    return "DocumentImpl[" + (virtualFile == null ? null : virtualFile.getName())+
           (isInEventsHandling() ? ",inEventHandling" : "") +
           (!myAssertThreading ? ",nonWriteThreadOnly" : "") +
           (myAcceptSlashR ? ",acceptSlashR" : "") +
           "]";
  }

  public @NotNull FrozenDocument freeze() {
    FrozenDocument frozen = myFrozen;
    if (frozen == null) {
      synchronized (myLineSetLock) {
        frozen = myFrozen;
        if (frozen == null) {
          myFrozen = frozen = new FrozenDocument(myText, myLineSet, myModificationStamp, dereference(myTextString));
        }
      }
    }
    return frozen;
  }

  public void assertNotInBulkUpdate() {
    if (myDoingBulkUpdate) throw new UnexpectedBulkUpdateStateException(myBulkUpdateEnteringTrace);
  }

  /**
   * RangeMarkerTree that keeps all intervals on weak references except the guarded blocks.
   * This class must be static because it should not capture 'this' reference to the document.
   * Otherwise, there will be a chain of hard references {@code file -> tree -> document} and gc won't collect the document
   */
  private static final class PersistentRangeMarkerTree extends RangeMarkerTree<RangeMarkerEx> {
    PersistentRangeMarkerTree(@NotNull Document document) {
      super(document);
    }

    @Override
    protected boolean keepIntervalOnWeakReference(@NotNull RangeMarkerEx interval) {
      // prevent guarded blocks to be collected by gc
      return !GuardedBlock.isGuarded(interval);
    }
  }

  private static final class UnexpectedBulkUpdateStateException extends RuntimeException implements ExceptionWithAttachments {
    private final Attachment[] myAttachments;

    private UnexpectedBulkUpdateStateException(Throwable enteringTrace) {
      super("Current operation is not permitted in bulk mode, see Document.isInBulkUpdate() javadoc");
      myAttachments = enteringTrace == null ? Attachment.EMPTY_ARRAY
                                            : new Attachment[] {new Attachment("enteringTrace.txt", enteringTrace)};
    }

    @Override
    public Attachment @NotNull [] getAttachments() {
      return myAttachments;
    }
  }
}
