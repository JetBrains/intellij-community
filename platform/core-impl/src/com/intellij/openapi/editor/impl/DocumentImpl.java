// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.reference.SoftReference;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.ImmutableCharSequence;
import gnu.trove.TIntIntHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class DocumentImpl extends UserDataHolderBase implements DocumentEx {
  private static final Logger LOG = Logger.getInstance(DocumentImpl.class);
  private static final int STRIP_TRAILING_SPACES_BULK_MODE_LINES_LIMIT = 1000;

  private final LockFreeCOWSortedArray<DocumentListener> myDocumentListeners =
    new LockFreeCOWSortedArray<>(PrioritizedDocumentListener.COMPARATOR, DocumentListener.ARRAY_FACTORY);
  private final RangeMarkerTree<RangeMarkerEx> myRangeMarkers = new RangeMarkerTree<>(this);
  private final RangeMarkerTree<RangeMarkerEx> myPersistentRangeMarkers = new RangeMarkerTree<>(this);
  private final List<RangeMarker> myGuardedBlocks = new ArrayList<>();
  private ReadonlyFragmentModificationHandler myReadonlyFragmentModificationHandler;

  @SuppressWarnings("RedundantStringConstructorCall") private final Object myLineSetLock = new String("line set lock");
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
    public CharSequence subSequence(int start, int end) {
      return myText.subSequence(start, end);
    }

    @NotNull
    @Override
    public String toString() {
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
   * NOTE: if client sets forUseInNonAWTThread to true it's supposed that client will completely control document and its listeners.
   * The noticeable peculiarity of DocumentImpl behavior in this mode is that DocumentImpl won't suppress ProcessCancelledException
   * thrown from listeners during changedUpdate event, so the exception will be rethrown and rest of the listeners WON'T be notified.
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

  static final Key<Reference<RangeMarkerTree<RangeMarkerEx>>> RANGE_MARKERS_KEY = Key.create("RANGE_MARKERS_KEY");
  static final Key<Reference<RangeMarkerTree<RangeMarkerEx>>> PERSISTENT_RANGE_MARKERS_KEY = Key.create("PERSISTENT_RANGE_MARKERS_KEY");
  public void documentCreatedFrom(@NotNull VirtualFile f) {
    processQueue();
    getSaveRMTree(f, RANGE_MARKERS_KEY, myRangeMarkers);
    getSaveRMTree(f, PERSISTENT_RANGE_MARKERS_KEY, myPersistentRangeMarkers);
  }

  // are some range markers retained by strong references?
  public static boolean areRangeMarkersRetainedFor(@NotNull VirtualFile f) {
    processQueue();
    // if a marker is retained then so is its node and the whole tree
    // (ignore the race when marker is gc-ed right after this call - it's harmless)
    return SoftReference.dereference(f.getUserData(RANGE_MARKERS_KEY)) != null
           || SoftReference.dereference(f.getUserData(PERSISTENT_RANGE_MARKERS_KEY)) != null;
  }

  private void getSaveRMTree(@NotNull VirtualFile f,
                             @NotNull Key<Reference<RangeMarkerTree<RangeMarkerEx>>> key, @NotNull RangeMarkerTree<RangeMarkerEx> tree) {
    RMTreeReference freshRef = new RMTreeReference(tree, f);
    Reference<RangeMarkerTree<RangeMarkerEx>> oldRef;
    do {
      oldRef = f.getUserData(key);
    }
    while (!f.replace(key, oldRef, freshRef));
    RangeMarkerTree<RangeMarkerEx> oldTree = SoftReference.dereference(oldRef);

    if (oldTree == null) {
      // no tree was saved in virtual file before. happens when created new document.
      // or the old tree got gc-ed, because no reachable markers retaining it are left alive. good riddance.
      return;
    }

    // old tree was saved in the virtual file. Have to transfer markers from there.
    TextRange myDocumentRange = new TextRange(0, getTextLength());
    oldTree.processAll(r ->{
      if (r.isValid() && myDocumentRange.contains(r)) {
        registerRangeMarker(r, r.getStartOffset(), r.getEndOffset(), r.isGreedyToLeft(), r.isGreedyToRight(), 0);
      }
      else {
        ((RangeMarkerImpl)r).invalidate("document was gc-ed and re-created");
      }
      return true;
    });
  }

  private static final ReferenceQueue<RangeMarkerTree<RangeMarkerEx>> rmTreeQueue = new ReferenceQueue<>();
  private static class RMTreeReference extends WeakReference<RangeMarkerTree<RangeMarkerEx>> {
    @NotNull private final VirtualFile virtualFile;

    RMTreeReference(@NotNull RangeMarkerTree<RangeMarkerEx> referent, @NotNull VirtualFile virtualFile) {
      super(referent, rmTreeQueue);
      this.virtualFile = virtualFile;
    }
  }
  static void processQueue() {
    RMTreeReference ref;
    while ((ref = (RMTreeReference)rmTreeQueue.poll()) != null) {
      ref.virtualFile.replace(RANGE_MARKERS_KEY, ref, null);
      ref.virtualFile.replace(PERSISTENT_RANGE_MARKERS_KEY, ref, null);
    }
  }

  /**
   * makes range marker without creating document (which could be expensive)
   */
  @NotNull
  static RangeMarker createRangeMarkerForVirtualFile(@NotNull VirtualFile file,
                                                     int startOffset,
                                                     int endOffset,
                                                     int startLine,
                                                     int startCol,
                                                     int endLine,
                                                     int endCol,
                                                     boolean persistent) {
    RangeMarkerImpl marker = persistent
                             ? new PersistentRangeMarker(file, startOffset, endOffset, startLine, startCol, endLine, endCol, false)
                             : new RangeMarkerImpl(file, startOffset, endOffset, false);
    Key<Reference<RangeMarkerTree<RangeMarkerEx>>> key = persistent ? PERSISTENT_RANGE_MARKERS_KEY : RANGE_MARKERS_KEY;
    RangeMarkerTree<RangeMarkerEx> tree;
    while (true) {
      Reference<RangeMarkerTree<RangeMarkerEx>> oldRef = file.getUserData(key);
      tree = SoftReference.dereference(oldRef);
      if (tree != null) break;
      tree = new RangeMarkerTree<>();
      RMTreeReference reference = new RMTreeReference(tree, file);
      if (file.replace(key, oldRef, reference)) break;
    }
    tree.addInterval(marker, startOffset, endOffset, false, false, false, 0);

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
   * @return true if stripping was completed successfully, false if the document prevented stripping by e.g. caret(s) being in the way
   */
  boolean stripTrailingSpaces(@Nullable final Project project, boolean inChangedLinesOnly, int @Nullable [] caretOffsets) {
    if (!isStripTrailingSpacesEnabled) {
      return true;
    }
    List<StripTrailingSpacesFilter> filters = new ArrayList<>();
    StripTrailingSpacesFilter specialFilter = null;
    for (StripTrailingSpacesFilterFactory filterFactory : StripTrailingSpacesFilterFactory.EXTENSION_POINT.getExtensions()) {
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

    TIntIntHashMap caretPositions = null;
    if (caretOffsets != null) {
      caretPositions = new TIntIntHashMap(caretOffsets.length);
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
      final int lineEnd = lineSet.getLineEnd(line) - lineSet.getSeparatorLength(line);
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

      final int finalStart = whiteSpaceStart + maxSpacesToLeave;
      if (finalStart < lineEnd) {
        targetOffsets[targetOffsetPos++] = finalStart;
        targetOffsets[targetOffsetPos++] = lineEnd;
      }
    }
    int finalTargetOffsetPos = targetOffsetPos;
    // document must be unblocked by now. If not, some Save handler attempted to modify PSI
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
      myPropertyChangeSupport.firePropertyChange(Document.PROP_WRITABLE, !isReadOnly, isReadOnly);
    }
  }

  ReadonlyFragmentModificationHandler getReadonlyFragmentModificationHandler() {
    return myReadonlyFragmentModificationHandler;
  }

  void setReadonlyFragmentModificationHandler(final ReadonlyFragmentModificationHandler readonlyFragmentModificationHandler) {
    myReadonlyFragmentModificationHandler = readonlyFragmentModificationHandler;
  }

  @Override
  public boolean isWritable() {
    return !myIsReadOnly;
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
  @NotNull
  public RangeMarker createGuardedBlock(int startOffset, int endOffset) {
    LOG.assertTrue(startOffset <= endOffset, "Should be startOffset <= endOffset");
    RangeMarker block = createRangeMarker(startOffset, endOffset, true);
    myGuardedBlocks.add(block);
    return block;
  }

  @Override
  public void removeGuardedBlock(@NotNull RangeMarker block) {
    myGuardedBlocks.remove(block);
  }

  @Override
  @NotNull
  public List<RangeMarker> getGuardedBlocks() {
    return myGuardedBlocks;
  }

  @Override
  public RangeMarker getOffsetGuard(int offset) {
    // Way too many garbage is produced otherwise in AbstractList.iterator()
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < myGuardedBlocks.size(); i++) {
      RangeMarker block = myGuardedBlocks.get(i);
      if (offsetInRange(offset, block.getStartOffset(), block.getEndOffset())) return block;
    }

    return null;
  }

  @Override
  public RangeMarker getRangeGuard(int start, int end) {
    for (RangeMarker block : myGuardedBlocks) {
      if (rangesIntersect(start, end, true, true,
                          block.getStartOffset(), block.getEndOffset(), block.isGreedyToLeft(), block.isGreedyToRight())) {
        return block;
      }
    }

    return null;
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

  private static boolean offsetInRange(int offset, int start, int end) {
    return start <= offset && offset < end;
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
  @NotNull
  public RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
    if (!(0 <= startOffset && startOffset <= endOffset && endOffset <= getTextLength())) {
      LOG.error("Incorrect offsets: startOffset=" + startOffset + ", endOffset=" + endOffset + ", text length=" + getTextLength());
    }
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
    replaceString(0, getTextLength(), chars, newModificationStamp, true); //TODO: optimization!!!
    clearLineModificationFlags();
  }

  @Override
  public void insertString(int offset, @NotNull CharSequence s) {
    insertString(offset, s, offset);
  }

  private void insertString(int offset, @NotNull CharSequence s, int moveSrcOffset) {
    if (offset < 0) throw new IndexOutOfBoundsException("Wrong offset: " + offset);
    if (offset > getTextLength()) {
      throw new IndexOutOfBoundsException(
        "Wrong offset: " + offset + "; documentLength: " + getTextLength() + "; " + s.subSequence(Math.max(0, s.length() - 20), s.length())
      );
    }
    assertWriteAccess();
    assertValidSeparators(s);

    if (!isWritable()) throw new ReadOnlyModificationException(this);
    if (s.length() == 0) return;

    RangeMarker marker = getRangeGuard(offset, offset);
    if (marker != null) {
      throwGuardedFragment(marker, offset, "", s);
    }

    ImmutableCharSequence newText = myText.insert(offset, s);
    ImmutableCharSequence newString = newText.subtext(offset, offset + s.length());
    updateText(newText, offset, "", newString, false, LocalTimeCounter.currentTime(),
               offset, 0, moveSrcOffset);
    trimToSize();
  }

  private void trimToSize() {
    if (myBufferSize != 0 && getTextLength() > myBufferSize) {
      deleteString(0, getTextLength() - myBufferSize);
    }
  }

  @Override
  public void deleteString(int startOffset, int endOffset) {
    deleteString(startOffset, endOffset, startOffset);
  }

  public void deleteString(int startOffset, int endOffset, int moveDstOffset) {
    assertBounds(startOffset, endOffset);

    assertWriteAccess();
    if (!isWritable()) throw new ReadOnlyModificationException(this);
    if (startOffset == endOffset) return;

    RangeMarker marker = getRangeGuard(startOffset, endOffset);
    if (marker != null) {
      throwGuardedFragment(marker, startOffset, myText.subSequence(startOffset, endOffset), "");
    }

    ImmutableCharSequence newText = myText.delete(startOffset, endOffset);
    ImmutableCharSequence oldString = myText.subtext(startOffset, endOffset);
    updateText(newText, startOffset, oldString, "", false, LocalTimeCounter.currentTime(),
               startOffset, endOffset - startOffset, moveDstOffset);
  }

  @Override
  public void moveText(int srcStart, int srcEnd, int dstOffset) {
    assertBounds(srcStart, srcEnd);
    if (dstOffset == srcStart || dstOffset == srcEnd) return;
    ProperTextRange srcRange = new ProperTextRange(srcStart, srcEnd);
    assert !srcRange.containsOffset(dstOffset) : "Can't perform text move from range [" + srcStart + "; " + srcEnd + ") to offset " + dstOffset;

    String replacement = getCharsSequence().subSequence(srcStart, srcEnd).toString();
    int shift = dstOffset < srcStart ? srcEnd - srcStart : 0;

    insertString(dstOffset, replacement, srcStart + shift);
    deleteString(srcStart + shift, srcEnd + shift, dstOffset);
  }

  @Override
  public void replaceString(int startOffset, int endOffset, @NotNull CharSequence s) {
    replaceString(startOffset, endOffset, s, LocalTimeCounter.currentTime(), false);
  }

  public void replaceString(int startOffset, int endOffset, @NotNull CharSequence s, final long newModificationStamp, boolean wholeTextReplaced) {
    assertBounds(startOffset, endOffset);

    assertWriteAccess();
    assertValidSeparators(s);

    if (!isWritable()) {
      throw new ReadOnlyModificationException(this);
    }

    int initialStartOffset = startOffset;
    int initialOldLength = endOffset - startOffset;

    final int newStringLength = s.length();
    final CharSequence chars = myText;
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
    RangeMarker guard = getRangeGuard(startOffset, endOffset);
    if (guard != null) {
      throwGuardedFragment(guard, startOffset, sToDelete, changedPart);
    }

    ImmutableCharSequence newText;
    if (wholeTextReplaced && s instanceof ImmutableCharSequence) {
      newText = (ImmutableCharSequence)s;
    }
    else {
      newText = myText.delete(startOffset, endOffset).insert(startOffset, changedPart);
      changedPart = newText.subtext(startOffset, startOffset + changedPart.length());
    }
    updateText(newText, startOffset, sToDelete, changedPart, wholeTextReplaced, newModificationStamp,
               initialStartOffset, initialOldLength, startOffset);
    trimToSize();
  }

  private void assertBounds(final int startOffset, final int endOffset) {
    if (startOffset < 0 || startOffset > getTextLength()) {
      throw new IndexOutOfBoundsException("Wrong startOffset: " + startOffset + "; documentLength: " + getTextLength());
    }
    if (endOffset < 0 || endOffset > getTextLength()) {
      throw new IndexOutOfBoundsException("Wrong endOffset: " + endOffset + "; documentLength: " + getTextLength());
    }
    if (endOffset < startOffset) {
      throw new IllegalArgumentException(
        "endOffset < startOffset: " + endOffset + " < " + startOffset + "; documentLength: " + getTextLength());
    }
  }

  private void assertWriteAccess() {
    if (myAssertThreading) {
      final Application application = ApplicationManager.getApplication();
      if (application != null) {
        application.assertWriteAccessAllowed();
        VirtualFile file = FileDocumentManager.getInstance().getFile(this);
        if (file != null && file.isInLocalFileSystem()) {
          ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();
        }
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
   * {@code 'remove text at document end'}. That means that when initial change will be actually performed, the document may be
   * not long enough to contain target range.
   * <p/>
   * Current method allows to check if document change is a {@code 'nested call'}.
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
      DocumentEvent event = new DocumentEventImpl(this, offset, oldString, newString, myModificationStamp, false);
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
    IntArrayList modifiedLines = new IntArrayList(caretLines.length);
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
      sequence.incrementAndGet(); // increment sequence before firing events so that modification sequence on commit will match this sequence now
      changedUpdate(event, newModificationStamp, prevText, exceptions);
    }
    finally {
      myChangeInProgress = false;
      exceptions.rethrowPCE();
    }
  }

  private class DelayedExceptions {
    Throwable myException;

    void register(Throwable e) {
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
        // the case of some wise inspection modifying non-physical document during highlighting to be interrupted
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

    if (!ShutDownTracker.isShutdownHookRunning()) {
      DocumentListener[] listeners = getListeners();
      for (int i = listeners.length - 1; i >= 0; i--) {
        try {
          listeners[i].beforeDocumentChange(event);
        }
        catch (Throwable e) {
          exceptions.register(e);
        }
      }
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

      if (!ShutDownTracker.isShutdownHookRunning()) {
        DocumentListener[] listeners = getListeners();
        for (DocumentListener listener : listeners) {
          try {
            listener.documentChanged(event);
          }
          catch (Throwable e) {
            exceptions.register(e);
          }
        }
      }
    }
    finally {
      myEventsHandling = false;
    }
  }

  @NotNull
  @Override
  public String getText() {
    return ReadAction.compute(this::doGetText);
  }

  @NotNull
  private String doGetText() {
    String s = SoftReference.dereference(myTextString);
    if (s == null) {
      myTextString = new SoftReference<>(s = myText.toString());
    }
    return s;
  }

  @NotNull
  @Override
  public String getText(@NotNull final TextRange range) {
    return ReadAction
      .compute(() -> myText.subSequence(range.getStartOffset(), range.getEndOffset()).toString());
  }

  @Override
  public int getTextLength() {
    return myText.length();
  }

  @Override
  @NotNull
  public CharSequence getCharsSequence() {
    return myMutableCharSequence;
  }

  @NotNull
  @Override
  public CharSequence getImmutableCharSequence() {
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
  public void addDocumentListener(@NotNull final DocumentListener listener, @NotNull Disposable parentDisposable) {
    addDocumentListener(listener);
    Disposer.register(parentDisposable, new DocumentListenerDisposable(myDocumentListeners, listener));
  }

  // this contortion is for avoiding document leak when the listener is leaked
  private static class DocumentListenerDisposable implements Disposable {
    @NotNull private final LockFreeCOWSortedArray<? super DocumentListener> myList;
    @NotNull private final DocumentListener myListener;

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
  public int getLineNumber(final int offset) {
    return getLineSet().findLineIndex(offset);
  }

  @Override
  @NotNull
  public LineIterator createLineIterator() {
    return getLineSet().createIterator();
  }

  @Override
  public final int getLineStartOffset(final int line) {
    if (line == 0) return 0; // otherwise it crashed for zero-length document
    return getLineSet().getLineStart(line);
  }

  @Override
  public final int getLineEndOffset(int line) {
    if (getTextLength() == 0 && line == 0) return 0;
    int result = getLineSet().getLineEnd(line) - getLineSeparatorLength(line);
    assert result >= 0;
    return result;
  }

  @Override
  public final int getLineSeparatorLength(int line) {
    int separatorLength = getLineSet().getSeparatorLength(line);
    assert separatorLength >= 0;
    return separatorLength;
  }

  @Override
  public final int getLineCount() {
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
  public void setText(@NotNull final CharSequence text) {
    Runnable runnable = () -> replaceString(0, getTextLength(), text, LocalTimeCounter.currentTime(), true);
    if (CommandProcessor.getInstance().isUndoTransparentActionInProgress()) {
      runnable.run();
    }
    else {
      CommandProcessor.getInstance().executeCommand(null, runnable, "", DocCommandGroupId.noneGroupId(this));
    }

    clearLineModificationFlags();
  }

  @Override
  public final boolean isInBulkUpdate() {
    return myDoingBulkUpdate;
  }

  @Override
  public final void setInBulkUpdate(boolean value) {
    if (myAssertThreading) {
      ApplicationManager.getApplication().assertIsWriteThread();
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

  @NotNull
  private static DocumentBulkUpdateListener getPublisher() {
    return DocumentBulkUpdateListenerHolder.ourBulkChangePublisher;
  }

  @Override
  public boolean processRangeMarkers(@NotNull Processor<? super RangeMarker> processor) {
    return processRangeMarkersOverlappingWith(0, getTextLength(), processor);
  }

  @Override
  public boolean processRangeMarkersOverlappingWith(int start, int end, @NotNull Processor<? super RangeMarker> processor) {
    TextRangeInterval interval = new TextRangeInterval(start, end);
    MarkupIterator<RangeMarkerEx> iterator = IntervalTreeImpl
      .mergingOverlappingIterator(myRangeMarkers, interval, myPersistentRangeMarkers, interval, RangeMarker.BY_START_OFFSET);
    try {
      return ContainerUtil.process(iterator, processor);
    }
    finally {
      iterator.dispose();
    }
  }

  @NotNull
  String dumpState() {
    @NonNls StringBuilder result = new StringBuilder();
    result.append(", intervals:\n");
    for (int line = 0; line < getLineCount(); line++) {
      result.append(line).append(": ").append(getLineStartOffset(line)).append("-")
        .append(getLineEndOffset(line)).append(", ");
    }
    if (result.length() > 0) {
      result.setLength(result.length() - 1);
    }
    return result.toString();
  }

  @Override
  public String toString() {
    return "DocumentImpl[" + FileDocumentManager.getInstance().getFile(this) + (isInEventsHandling() ? ",inEventHandling" : "") + "]";
  }

  @NotNull
  public FrozenDocument freeze() {
    FrozenDocument frozen = myFrozen;
    if (frozen == null) {
      synchronized (myLineSetLock) {
        frozen = myFrozen;
        if (frozen == null) {
          myFrozen = frozen = new FrozenDocument(myText, myLineSet, myModificationStamp, SoftReference.dereference(myTextString));
        }
      }
    }
    return frozen;
  }

  public void assertNotInBulkUpdate() {
    if (myDoingBulkUpdate) throw new UnexpectedBulkUpdateStateException(myBulkUpdateEnteringTrace);
  }

  private static final class UnexpectedBulkUpdateStateException extends RuntimeException implements ExceptionWithAttachments {
    private final Attachment[] myAttachments;

    private UnexpectedBulkUpdateStateException(Throwable enteringTrace) {
      super("Current operation is not permitted in bulk mode, see Document.setInBulkUpdate javadoc");
      myAttachments = enteringTrace == null ? Attachment.EMPTY_ARRAY
                                            : new Attachment[] {new Attachment("enteringTrace.txt", enteringTrace)};
    }

    @Override
    public Attachment @NotNull [] getAttachments() {
      return myAttachments;
    }
  }
}
