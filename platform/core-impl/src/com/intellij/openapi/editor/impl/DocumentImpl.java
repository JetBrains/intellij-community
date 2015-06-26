/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.reference.SoftReference;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.ImmutableText;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DocumentImpl extends UserDataHolderBase implements DocumentEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.DocumentImpl");

  private final Ref<DocumentListener[]> myCachedDocumentListeners = Ref.create(null);
  private final List<DocumentListener> myDocumentListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final RangeMarkerTree<RangeMarkerEx> myRangeMarkers = new RangeMarkerTree<RangeMarkerEx>(this);
  private final RangeMarkerTree<RangeMarkerEx> myPersistentRangeMarkers = new RangeMarkerTree<RangeMarkerEx>(this);
  private final List<RangeMarker> myGuardedBlocks = new ArrayList<RangeMarker>();
  private ReadonlyFragmentModificationHandler myReadonlyFragmentModificationHandler;

  private final Object myLineSetLock = new String("line set lock");
  private volatile LineSet myLineSet;
  private volatile ImmutableText myText;
  private volatile SoftReference<String> myTextString;

  private boolean myIsReadOnly = false;
  private volatile boolean isStripTrailingSpacesEnabled = true;
  private volatile long myModificationStamp;
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

  private final List<EditReadOnlyListener> myReadOnlyListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private volatile boolean myMightContainTabs = true; // optimisation flag: when document contains no tabs it is dramatically easier to calculate positions in editor
  private int myTabTrackingRequestors = 0;

  private int myCheckGuardedBlocks = 0;
  private boolean myGuardsSuppressed = false;
  private boolean myEventsHandling = false;
  private final boolean myAssertThreading;
  private volatile boolean myDoingBulkUpdate = false;
  private boolean myUpdatingBulkModeStatus;
  private volatile boolean myAcceptSlashR = false;
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

  public DocumentImpl(@NotNull String text) {
    this(text, false);
  }

  public DocumentImpl(@NotNull CharSequence chars) {
    this(chars, false);
  }

  public DocumentImpl(@NotNull CharSequence chars, boolean forUseInNonAWTThread) {
    this(chars, false, forUseInNonAWTThread);
  }

  public DocumentImpl(@NotNull CharSequence chars, boolean acceptSlashR, boolean forUseInNonAWTThread) {
    setAcceptSlashR(acceptSlashR);
    assertValidSeparators(chars);
    myText = ImmutableText.valueOf(chars);
    setCyclicBufferSize(0);
    setModificationStamp(LocalTimeCounter.currentTime());
    myAssertThreading = !forUseInNonAWTThread;
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
          lineSet = new LineSet();
          lineSet.documentCreated(this);
          myLineSet = lineSet;
        }
      }
    }

    return lineSet;
  }

  @Override
  @NotNull
  public char[] getChars() {
    return CharArrayUtil.fromSequence(myText);
  }

  @Override
  public void setStripTrailingSpacesEnabled(boolean isEnabled) {
    isStripTrailingSpacesEnabled = isEnabled;
  }

  @TestOnly
  public boolean stripTrailingSpaces(Project project) {
    return stripTrailingSpaces(project, false, false, new int[0]);
  }

  /**
   * @return true if stripping was completed successfully, false if the document prevented stripping by e.g. caret(s) being in the way
   */
  boolean stripTrailingSpaces(@Nullable final Project project,
                              boolean inChangedLinesOnly,
                              boolean virtualSpaceEnabled,
                              @NotNull int[] caretOffsets) {
    if (!isStripTrailingSpacesEnabled) {
      return true;
    }

    boolean markAsNeedsStrippingLater = false;
    CharSequence text = myText;
    TIntObjectHashMap<List<RangeMarker>> caretMarkers = new TIntObjectHashMap<List<RangeMarker>>(caretOffsets.length);
    try {
      if (!virtualSpaceEnabled) {
        for (int caretOffset : caretOffsets) {
          if (caretOffset < 0 || caretOffset > getTextLength()) {
            continue;
          }
          int line = getLineNumber(caretOffset);
          List<RangeMarker> markers = caretMarkers.get(line);
          if (markers == null) {
            markers = new ArrayList<RangeMarker>();
            caretMarkers.put(line, markers);
          }
          RangeMarker marker = createRangeMarker(caretOffset, caretOffset);
          markers.add(marker);
        }
      }
      LineSet lineSet = getLineSet();
      lineLoop:
      for (int line = 0; line < lineSet.getLineCount(); line++) {
        if (inChangedLinesOnly && !lineSet.isModified(line)) continue;
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
        if (!virtualSpaceEnabled) {
          List<RangeMarker> markers = caretMarkers.get(line);
          if (markers != null) {
            for (RangeMarker marker : markers) {
              if (marker.getStartOffset() >= 0 && whiteSpaceStart < marker.getStartOffset()) {
                // mark this as a document that needs stripping later
                // otherwise the caret would jump madly
                markAsNeedsStrippingLater = true;
                continue lineLoop;
              }
            }
          }
        }
        final int finalStart = whiteSpaceStart;
        // document must be unblocked by now. If not, some Save handler attempted to modify PSI
        // which should have been caught by assertion in com.intellij.pom.core.impl.PomModelImpl.runTransaction
        DocumentUtil.writeInRunUndoTransparentAction(new DocumentRunnable(DocumentImpl.this, project) {
          @Override
          public void run() {
            deleteString(finalStart, lineEnd);
          }
        });
        text = myText;
      }
    }
    finally {
      caretMarkers.forEachValue(new TObjectProcedure<List<RangeMarker>>() {
        @Override
        public boolean execute(List<RangeMarker> markerList) {
          if (markerList != null) {
            for (RangeMarker marker : markerList) {
              try {
                marker.dispose();
              }
              catch (Exception e) {
                LOG.error(e);
              }
            }
          }
          return true;
        }
      });
    }
    return markAsNeedsStrippingLater;
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

  private  RangeMarkerTree<RangeMarkerEx> treeFor(@NotNull RangeMarkerEx rangeMarker) {
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
    treeFor(rangeMarker).addInterval(rangeMarker, start, end, greedyToLeft, greedyToRight, layer);
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
  @SuppressWarnings({"ForLoopReplaceableByForEach"}) // Way too many garbage is produced otherwise in AbstractList.iterator()
  public RangeMarker getOffsetGuard(int offset) {
    for (int i = 0; i < myGuardedBlocks.size(); i++) {
      RangeMarker block = myGuardedBlocks.get(i);
      if (offsetInRange(offset, block.getStartOffset(), block.getEndOffset())) return block;
    }

    return null;
  }

  @Override
  public RangeMarker getRangeGuard(int start, int end) {
    for (RangeMarker block : myGuardedBlocks) {
      if (rangesIntersect(start, true, block.getStartOffset(), block.isGreedyToLeft(), end, true, block.getEndOffset(),
                          block.isGreedyToRight())) {
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

  private static boolean rangesIntersect(int start0, boolean leftInclusive0,
                                         int start1, boolean leftInclusive1,
                                         int end0, boolean rightInclusive0,
                                         int end1, boolean rightInclusive1) {
    if (start0 > start1 || start0 == start1 && !leftInclusive0) {
      return rangesIntersect(start1, leftInclusive1, start0, leftInclusive0, end1, rightInclusive1, end0, rightInclusive0);
    }
    if (end0 == start1) return leftInclusive1 && rightInclusive0;
    return end0 > start1;
  }

  @Override
  @NotNull
  public RangeMarker createRangeMarker(int startOffset, int endOffset) {
    return createRangeMarker(startOffset, endOffset, false);
  }

  @Override
  @NotNull
  public RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
    if (!(0 <= startOffset && startOffset <= endOffset && endOffset <= getTextLength())) {
      LOG.error("Incorrect offsets: startOffset=" + startOffset + ", endOffset=" + endOffset + ", text length=" + getTextLength());
    }
    return surviveOnExternalChange
           ? new PersistentRangeMarker(this, startOffset, endOffset, true)
           : new RangeMarkerImpl(this, startOffset, endOffset, true);
  }

  @Override
  public long getModificationStamp() {
    return myModificationStamp;
  }

  @Override
  public void setModificationStamp(long modificationStamp) {
    myModificationStamp = modificationStamp;
  }

  @Override
  public void replaceText(@NotNull CharSequence chars, long newModificationStamp) {
    replaceString(0, getTextLength(), chars, newModificationStamp, true); //TODO: optimization!!!
    clearLineModificationFlags();
  }

  @Override
  public int getListenersCount() {
    return myDocumentListeners.size();
  }

  @Override
  public void insertString(int offset, @NotNull CharSequence s) {
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
      throwGuardedFragment(marker, offset, null, s.toString());
    }

    myText = myText.ensureChunked();
    ImmutableText newText = myText.insert(offset, ImmutableText.valueOf(s));
    updateText(newText, offset, null, newText.subtext(offset, offset + s.length()), false, LocalTimeCounter.currentTime());
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
    if (!isWritable()) throw new ReadOnlyModificationException(this);
    if (startOffset == endOffset) return;

    RangeMarker marker = getRangeGuard(startOffset, endOffset);
    if (marker != null) {
      throwGuardedFragment(marker, startOffset, myText.subSequence(startOffset, endOffset).toString(), null);
    }

    myText = myText.ensureChunked();
    updateText(myText.delete(startOffset, endOffset), startOffset, myText.subtext(startOffset, endOffset), null, false, LocalTimeCounter.currentTime());
  }

  @Override
  public void moveText(int srcStart, int srcEnd, int dstOffset) {
    assertBounds(srcStart, srcEnd);
    if (dstOffset == srcEnd) return;
    ProperTextRange srcRange = new ProperTextRange(srcStart, srcEnd);
    assert !srcRange.containsOffset(dstOffset) : "Can't perform text move from range [" +srcStart+ "; " + srcEnd+ ") to offset "+dstOffset;

    String replacement = getCharsSequence().subSequence(srcStart, srcEnd).toString();

    insertString(dstOffset, replacement);
    int shift = 0;
    if (dstOffset < srcStart) {
      shift = srcEnd - srcStart;
    }
    fireMoveText(srcStart + shift, srcEnd + shift, dstOffset);

    deleteString(srcStart + shift, srcEnd + shift);
  }

  private void fireMoveText(int start, int end, int newBase) {
    for (DocumentListener listener : getCachedListeners()) {
      if (listener instanceof PrioritizedInternalDocumentListener) {
        ((PrioritizedInternalDocumentListener)listener).moveTextHappened(start, end, newBase);
      }
    }
  }

  @Override
  public void replaceString(int startOffset, int endOffset, @NotNull CharSequence s) {
    replaceString(startOffset, endOffset, s, LocalTimeCounter.currentTime(), false);
  }

  private void replaceString(int startOffset, int endOffset, @NotNull CharSequence s, final long newModificationStamp, boolean wholeTextReplaced) {
    assertBounds(startOffset, endOffset);

    assertWriteAccess();
    assertValidSeparators(s);

    if (!isWritable()) {
      throw new ReadOnlyModificationException(this);
    }

    final int newStringLength = s.length();
    final CharSequence chars = getCharsSequence();
    int newStartInString = 0;
    int newEndInString = newStringLength;
    while (newStartInString < newStringLength &&
           startOffset < endOffset &&
           s.charAt(newStartInString) == chars.charAt(startOffset)) {
      startOffset++;
      newStartInString++;
    }

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
      throwGuardedFragment(guard, startOffset, sToDelete.toString(), changedPart.toString());
    }

    ImmutableText newText;
    if (wholeTextReplaced && s instanceof ImmutableText) {
      newText = (ImmutableText)s;
    }
    else {
      myText = myText.ensureChunked();
      newText = myText.delete(startOffset, endOffset).insert(startOffset, changedPart);
      changedPart = newText.subtext(startOffset, startOffset + changedPart.length());
    }
    updateText(newText, startOffset, sToDelete, changedPart, wholeTextReplaced, newModificationStamp);
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
   * There is a possible case that <code>'before change'</code> notification produces new change. We have a problem then - imagine
   * that initial change was <code>'replace particular range at document end'</code> and <code>'nested change'</code> was to
   * <code>'remove text at document end'</code>. That means that when initial change will be actually performed, the document may be
   * not long enough to contain target range.
   * <p/>
   * Current method allows to check if document change is a <code>'nested call'</code>.
   *
   * @throws IllegalStateException if this method is called during a <code>'nested document modification'</code>
   */
  private void assertNotNestedModification() throws IllegalStateException {
    if (myChangeInProgress) {
      throw new IllegalStateException("Detected document modification from DocumentListener");
    }
  }

  private void throwGuardedFragment(@NotNull RangeMarker guard, int offset, String oldString, String newString) {
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
    getLineSet().clearModificationFlags();
  }

  void clearLineModificationFlagsExcept(@NotNull int[] caretLines) {
    IntArrayList modifiedLines = new IntArrayList(caretLines.length);
    LineSet lineSet = getLineSet();
    for (int line : caretLines) {
      if (line >= 0 && line < lineSet.getLineCount() && lineSet.isModified(line)) {
        modifiedLines.add(line);
      }
    }
    clearLineModificationFlags();
    for (int i = 0; i < modifiedLines.size(); i++) {
      lineSet.setModified(modifiedLines.get(i));
    }
  }

  private void updateText(@NotNull ImmutableText newText,
                          int offset,
                          @Nullable CharSequence oldString,
                          @Nullable CharSequence newString,
                          boolean wholeTextReplaced,
                          long newModificationStamp) {
    assertNotNestedModification();
    boolean enableRecursiveModifications = Registry.is("enable.recursive.document.changes"); // temporary property, to remove in IDEA 16
    myChangeInProgress = true;
    try {
      final DocumentEvent event;
      try {
        event = doBeforeChangedUpdate(offset, oldString, newString, wholeTextReplaced);
      }
      finally {
        if (enableRecursiveModifications) {
          myChangeInProgress = false;
        }
      }
      myTextString = null;
      myText = newText;
      changedUpdate(event, newModificationStamp);
    }
    finally {
      if (!enableRecursiveModifications) {
        myChangeInProgress = false;
      }
    }
  }

  @NotNull
  private DocumentEvent doBeforeChangedUpdate(int offset, CharSequence oldString, CharSequence newString, boolean wholeTextReplaced) {
    Application app = ApplicationManager.getApplication();
    if (app != null) {
      FileDocumentManager manager = FileDocumentManager.getInstance();
      VirtualFile file = manager.getFile(this);
      if (file != null && !file.isValid()) {
        LOG.error("File of this document has been deleted.");
      }
    }
    assertInsideCommand();

    getLineSet(); // initialize line set to track changed lines

    DocumentEvent event = new DocumentEventImpl(this, offset, oldString, newString, myModificationStamp, wholeTextReplaced);

    if (!ShutDownTracker.isShutdownHookRunning()) {
      DocumentListener[] listeners = getCachedListeners();
      for (int i = listeners.length - 1; i >= 0; i--) {
        try {
          listeners[i].beforeDocumentChange(event);
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }

    myEventsHandling = true;
    return event;
  }

  private void assertInsideCommand() {
    if (!myAssertThreading) return;
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    if (!commandProcessor.isUndoTransparentActionInProgress() &&
        commandProcessor.getCurrentCommand() == null) {
      throw new IncorrectOperationException("Must not change document outside command or undo-transparent action. See com.intellij.openapi.command.WriteCommandAction or com.intellij.openapi.command.CommandProcessor");
    }
  }

  private void changedUpdate(@NotNull DocumentEvent event, long newModificationStamp) {
    try {
      if (LOG.isDebugEnabled()) LOG.debug(event.toString());

      getLineSet().changedUpdate(event);
      if (myTabTrackingRequestors > 0) {
        updateMightContainTabs(event.getNewFragment());
      }
      setModificationStamp(newModificationStamp);

      if (!ShutDownTracker.isShutdownHookRunning()) {
        DocumentListener[] listeners = getCachedListeners();
        for (DocumentListener listener : listeners) {
          try {
            listener.documentChanged(event);
          }
          catch (Throwable e) {
            LOG.error(e);
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
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return doGetText();
      }
    });
  }

  @NotNull
  private String doGetText() {
    String s = SoftReference.dereference(myTextString);
    if (s == null) {
      myTextString = new SoftReference<String>(s = myText.toString());
    }
    return s;
  }

  @NotNull
  @Override
  public String getText(@NotNull final TextRange range) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return myText.subSequence(range.getStartOffset(), range.getEndOffset()).toString();
      }
    });
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
    myCachedDocumentListeners.set(null);

    if (myDocumentListeners.contains(listener)) {
      LOG.error("Already registered: " + listener);
    }
    boolean added = myDocumentListeners.add(listener);
    LOG.assertTrue(added, listener);
  }

  @Override
  public void addDocumentListener(@NotNull final DocumentListener listener, @NotNull Disposable parentDisposable) {
    addDocumentListener(listener);
    Disposer.register(parentDisposable, new DocumentListenerDisposable(listener, myCachedDocumentListeners, myDocumentListeners));
  }

  private static class DocumentListenerDisposable implements Disposable {
    private final DocumentListener myListener;
    private final Ref<DocumentListener[]> myCachedDocumentListenersRef;
    private final List<DocumentListener> myDocumentListeners;

    private DocumentListenerDisposable(@NotNull DocumentListener listener,
                                       @NotNull Ref<DocumentListener[]> cachedDocumentListenersRef,
                                       @NotNull List<DocumentListener> documentListeners) {
      myListener = listener;
      myCachedDocumentListenersRef = cachedDocumentListenersRef;
      myDocumentListeners = documentListeners;
    }

    @Override
    public void dispose() {
      doRemoveDocumentListener(myListener, myCachedDocumentListenersRef, myDocumentListeners);
    }
  }

  @Override
  public void removeDocumentListener(@NotNull DocumentListener listener) {
    doRemoveDocumentListener(listener, myCachedDocumentListeners, myDocumentListeners);
  }

  private static void doRemoveDocumentListener(@NotNull DocumentListener listener,
                                               @NotNull Ref<DocumentListener[]> cachedDocumentListenersRef,
                                               @NotNull List<DocumentListener> documentListeners) {
    cachedDocumentListenersRef.set(null);
    boolean success = documentListeners.remove(listener);
    if (!success) {
      LOG.error("Can't remove document listener (" + listener + "). Registered listeners: " + documentListeners);
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
    if (getTextLength() == 0) return 0;
    int lineCount = getLineSet().getLineCount();
    assert lineCount >= 0;
    return lineCount;
  }

  @NotNull
  private DocumentListener[] getCachedListeners() {
    DocumentListener[] cachedListeners = myCachedDocumentListeners.get();
    if (cachedListeners == null) {
      DocumentListener[] listeners = ArrayUtil.stripTrailingNulls(myDocumentListeners.toArray(new DocumentListener[myDocumentListeners.size()]));
      Arrays.sort(listeners, PrioritizedDocumentListener.COMPARATOR);
      cachedListeners = listeners;
      myCachedDocumentListeners.set(cachedListeners);
    }

    return cachedListeners;
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
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        replaceString(0, getTextLength(), text, LocalTimeCounter.currentTime(), true);
      }
    };
    if (CommandProcessor.getInstance().isUndoTransparentActionInProgress()) {
      runnable.run();
    }
    else {
      CommandProcessor.getInstance().executeCommand(null, runnable, "", DocCommandGroupId.noneGroupId(this));
    }

    clearLineModificationFlags();
  }

  @Override
  @NotNull
  public RangeMarker createRangeMarker(@NotNull final TextRange textRange) {
    return createRangeMarker(textRange.getStartOffset(), textRange.getEndOffset());
  }

  @Override
  public final boolean isInBulkUpdate() {
    return myDoingBulkUpdate;
  }

  @Override
  public final void setInBulkUpdate(boolean value) {
    if (myAssertThreading) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
    if (myDoingBulkUpdate == value) {
      // do not fire listeners or otherwise updateStarted() will be called more times than updateFinished()
      return;
    }
    if (myUpdatingBulkModeStatus) {
      throw new IllegalStateException("Detected bulk mode status update from DocumentBulkUpdateListener");
    }
    myUpdatingBulkModeStatus = true;
    try {
      myDoingBulkUpdate = value;
      if (value) {
        getPublisher().updateStarted(this);
      }
      else {
        getPublisher().updateFinished(this);
      }
    }
    finally {
      myUpdatingBulkModeStatus = false;
    }
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
  public boolean processRangeMarkers(@NotNull Processor<RangeMarker> processor) {
    return processRangeMarkersOverlappingWith(0, getTextLength(), processor);
  }

  @Override
  public boolean processRangeMarkersOverlappingWith(int start, int end, @NotNull Processor<RangeMarker> processor) {
    TextRangeInterval interval = new TextRangeInterval(start, end);
    IntervalTreeImpl.PeekableIterator<RangeMarkerEx> iterator = IntervalTreeImpl
      .mergingOverlappingIterator(myRangeMarkers, interval, myPersistentRangeMarkers, interval, RangeMarker.BY_START_OFFSET);
    try {
      return ContainerUtil.process(iterator, processor);
    }
    finally {
      iterator.dispose();
    }
  }

  @NotNull
  public String dumpState() {
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
    return "DocumentImpl[" + FileDocumentManager.getInstance().getFile(this) + "]";
  }

  void requestTabTracking() {
    if (myAssertThreading) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
    if (myTabTrackingRequestors++ == 0) {
      myMightContainTabs = false;
      updateMightContainTabs(myText);
    }
  }

  void giveUpTabTracking() {
    if (myAssertThreading) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
    if (--myTabTrackingRequestors == 0) {
      myMightContainTabs = true;
    }
  }

  boolean mightContainTabs() {
    return myMightContainTabs;
  }

  private void updateMightContainTabs(CharSequence text) {
    if (!myMightContainTabs) {
      myMightContainTabs = StringUtil.contains(text, 0, text.length(), '\t');
    }
  }
}
