// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler;
import com.intellij.openapi.editor.elf.ElfFeatureFlag;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.DocumentCore;
import com.intellij.openapi.editor.ex.EditReadOnlyListener;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * For historical reasons, this class is public, being an internal implementation {@link DocumentEx}.
 * <p>
 * Since 263 the actual implementation has been hidden behind an interface.
 * All methods delegate to {@link #impl}.
 * Never add a new field or method implementation here but use {@link DocumentCore} to add new functionality.
 */
public final class DocumentImpl extends UserDataHolderBase implements DocumentEx {

  /**
   * Actual document implementation hidden behind an interface
   */
  private final DocumentCore impl;

  /**
   * Document reference used for communication with the outside world: document listeners, EPs, CommandProcessor, etc.
   * <ul>
   *   <li>
   *     {@code null} value means {@code this} and {@code hostDocument} are the same.
   *     It is the default old behavior for all documents.
   *   </li>
   *   <li>
   *     {@code non-null} value means {@code this} is a "view" over the corresponding {@code hostDocument}.
   *     E.g., listeners of {@code this} are notified with {@code documentEvent.getDocument() == hostDocument}.
   *     It is an experimental feature used for supporting editor lock-free typing feature
   *   </li>
   * </ul>
   */
  private final @Nullable DocumentImpl hostDocument;

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
    this(
      (forUseInNonAWTThread || !ElfFeatureFlag.isEnabled())
      ? DocumentCoreImpl.createCore(chars, acceptSlashR, forUseInNonAWTThread)
      : DocumentMagicCoreImpl.createCore(chars, acceptSlashR, forUseInNonAWTThread)
    );
  }

  private DocumentImpl(@NotNull DocumentCore impl) {
    this(impl, null);
  }

  /**
   * @param hostDocument null if this document is the host,
   *                     non-null if this document is a "view" over the corresponding host document
   */
  @ApiStatus.Internal
  public DocumentImpl(@NotNull DocumentCore impl, @Nullable DocumentImpl hostDocument) {
    this.impl = impl;
    this.hostDocument = hostDocument;
  }

  @Override
  public @NotNull CharSequence getImmutableCharSequence() {
    return impl.snapshot().text();
  }

  @Override
  public @NotNull CharSequence getCharsSequence() {
    return impl.live();
  }

  @Override
  public @NotNull String getText() {
    return impl.snapshot().string();
  }

  @Override
  public @NotNull String getText(@NotNull TextRange range) {
    return impl.snapshot().string(range);
  }

  @Override
  public int getTextLength() {
    return impl.snapshot().textLength();
  }

  @Override
  public int getLineCount() {
    return impl.snapshot().lineCount();
  }

  @Override
  public int getLineNumber(int offset) {
    return impl.snapshot().lineNumber(offset);
  }

  @Override
  public int getLineStartOffset(int line) {
    return impl.snapshot().lineStartOffset(line);
  }

  @Override
  public int getLineEndOffset(int line) {
    return impl.snapshot().lineEndOffset(line);
  }

  @Override
  public int getLineSeparatorLength(int line) {
    return impl.snapshot().lineSeparatorLength(line);
  }

  @Override
  public @NotNull LineIterator createLineIterator() {
    return impl.snapshot().lineIterator();
  }

  @Override
  public void insertString(int offset, @NotNull CharSequence s) {
    impl.mutator().insertString(hostDocument(), offset, s);
  }

  @Override
  public void deleteString(int startOffset, int endOffset) {
    impl.mutator().deleteString(hostDocument(), startOffset, endOffset);
  }

  @Override
  public void replaceString(int startOffset, int endOffset, @NotNull CharSequence s) {
    replaceString(startOffset, endOffset, startOffset, s, DocumentModStamp.next(), false);
  }

  @Override
  public void replaceText(@NotNull CharSequence chars, long newModificationStamp) {
    impl.mutator().replaceText(hostDocument(), chars, newModificationStamp);
  }

  @Override
  public void moveText(int srcStart, int srcEnd, int dstOffset) {
    impl.mutator().moveText(hostDocument(), srcStart, srcEnd, dstOffset);
  }

  @Override
  public void setText(@NotNull CharSequence text) {
    impl.mutator().setText(hostDocument(), text);
  }

  @Override
  public boolean isInEventsHandling() {
    return impl.dispatcher().firingTextChanged();
  }

  @Override
  public int getModificationSequence() {
    return impl.snapshot().modSequence();
  }

  @Override
  public long getModificationStamp() {
    return impl.snapshot().modStamp();
  }

  @Override
  public void setModificationStamp(long modificationStamp) {
    impl.mutator().setModStamp(modificationStamp, false);
  }

  @Override
  public boolean isLineModified(int line) {
    return impl.snapshot().isLineModified(line);
  }

  @Override
  public void clearLineModificationFlags() {
    impl.mutator().clearLineFlags(0, Integer.MAX_VALUE, ArrayUtil.EMPTY_INT_ARRAY);
  }

  @Override
  public @NotNull RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
    return impl.tree().createRangeMarker(hostDocument(), startOffset, endOffset, surviveOnExternalChange);
  }

  @Override
  public boolean removeRangeMarker(@NotNull RangeMarkerEx rangeMarker) {
    return impl.tree().removeRangeMarker(rangeMarker);
  }

  @Override
  public void registerRangeMarker(
    @NotNull RangeMarkerEx rangeMarker,
    int start,
    int end,
    boolean greedyToLeft,
    boolean greedyToRight,
    int layer
  ) {
    impl.tree().registerRangeMarker(rangeMarker, start, end, greedyToLeft, greedyToRight, layer);
  }

  @Override
  public boolean processRangeMarkers(@NotNull Processor<? super RangeMarker> processor) {
    return processRangeMarkersOverlappingWith(0, getTextLength(), processor);
  }

  @Override
  public boolean processRangeMarkersOverlappingWith(int start, int end, @NotNull Processor<? super RangeMarker> processor) {
    return impl.tree().processRangeMarkersOverlappingWith(start, end, processor);
  }

  @Override
  public void addDocumentListener(@NotNull DocumentListener listener, @NotNull Disposable parentDisposable) {
    impl.dispatcher().addDocumentListener(listener, parentDisposable);
  }

  @SuppressWarnings("deprecation") // impl of deprecated method
  @Override
  public void addDocumentListener(@NotNull DocumentListener listener) {
    impl.dispatcher().addDocumentListener(listener);
  }

  @Override
  public void removeDocumentListener(@NotNull DocumentListener listener) {
    impl.dispatcher().removeDocumentListener(listener);
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    impl.dispatcher().addPropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    impl.dispatcher().removePropertyChangeListener(listener);
  }

  @Override
  public void addEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
    impl.dispatcher().addEditReadOnlyListener(listener);
  }

  @Override
  public void removeEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
    impl.dispatcher().removeEditReadOnlyListener(listener);
  }

  @Override
  public void fireReadOnlyModificationAttempt() {
    impl.dispatcher().fireReadOnlyModificationAttempt(hostDocument());
  }

  @Override
  public boolean isInBulkUpdate() {
    return impl.dispatcher().isInBulkUpdate();
  }

  @SuppressWarnings("deprecation") // impl of deprecated method
  @Override
  public void setInBulkUpdate(boolean status) {
    impl.dispatcher().setBulkModeStatus(hostDocument(), status);
  }

  @Override
  public @NotNull RangeMarker createGuardedBlock(int startOffset, int endOffset) {
    return impl.tree().createGuardedBlock(hostDocument(), startOffset, endOffset);
  }

  @Override
  public void removeGuardedBlock(@NotNull RangeMarker block) {
    impl.tree().removeGuardedBlock(block);
  }

  @Override
  public @NotNull List<RangeMarker> getGuardedBlocks() {
    return impl.tree().getGuardedBlocks();
  }

  @Override
  public @Nullable RangeMarker getOffsetGuard(int offset) {
    return impl.tree().getOffsetGuard(offset);
  }

  @Override
  public @Nullable RangeMarker getRangeGuard(int start, int end) {
    return impl.tree().getRangeGuard(start, end);
  }

  @Override
  public void startGuardedBlockChecking() {
    impl.settings().startGuardCheck();
  }

  @Override
  public void stopGuardedBlockChecking() {
    impl.settings().stopGuardCheck();
  }

  @Override
  public void setReadOnly(boolean isReadOnly) {
    boolean old = impl.settings().setReadOnly(isReadOnly);
    if (old != isReadOnly) {
      impl.dispatcher().firePropertyChange(hostDocument(), isReadOnly);
    }
  }

  @Override
  public boolean isWritable() {
    return impl.settings().isWritable(hostDocument());
  }

  @Override
  public void setStripTrailingSpacesEnabled(boolean isEnabled) {
    impl.settings().setStripTrailingSpaces(isEnabled);
  }

  @Override
  public void setCyclicBufferSize(int bufferSize) {
    impl.settings().setCycleBufferSize(bufferSize);
  }

  public boolean setAcceptSlashR(boolean accept) {
    return impl.settings().setSlashRAllowed(accept);
  }

  public boolean acceptsSlashR() {
    return impl.settings().isSlashRAllowed();
  }

  public void clearLineModificationFlags(int startLine, int endLine) {
    impl.mutator().clearLineFlags(startLine, endLine, ArrayUtil.EMPTY_INT_ARRAY);
  }

  public @NotNull String dumpState() {
    return impl.snapshot().dumpState();
  }

  public void assertNotInBulkUpdate() {
    impl.dispatcher().assertNotInBulkUpdate();
  }

  @ApiStatus.Internal
  @Override
  public void suppressGuardedExceptions(boolean onlyWholeText) {
    impl.settings().suppressGuardCheck(onlyWholeText);
  }

  @ApiStatus.Internal
  @Override
  public void unSuppressGuardedExceptions(boolean onlyWholeText) {
    impl.settings().unsuppressGuardCheck(onlyWholeText);
  }

  @ApiStatus.Internal
  public void documentCreatedFrom(@NotNull VirtualFile f, int tabSize) {
    impl.tree().restoreRangeMarkersFromFile(f, hostDocument(), tabSize);
  }

  @ApiStatus.Internal
  public void replaceString(int startOffset, int endOffset, int moveOffset, @NotNull CharSequence s, long newModificationStamp, boolean wholeTextReplaced) {
    impl.mutator().replaceString(hostDocument(), startOffset, endOffset, moveOffset, s, newModificationStamp, wholeTextReplaced);
  }

  /**
   * @return true if stripping was completed successfully, false if the document prevented stripping by e.g., caret(s) being in the way
   */
  @ApiStatus.Internal
  public boolean stripTrailingSpaces(@Nullable Project project, boolean inChangedLinesOnly, int @Nullable [] caretOffsets) {
    if (!impl.settings().isStripTrailingSpacesEnabled()) {
      return true;
    }
    return StripTrailingSpacesUtil.stripTrailingSpaces(project, hostDocument(), inChangedLinesOnly, caretOffsets);
  }

  @ApiStatus.Internal
  public @Nullable ReadonlyFragmentModificationHandler getReadonlyFragmentModificationHandler() {
    return impl.settings().readOnlyHandler();
  }

  @ApiStatus.Internal
  public void setReadonlyFragmentModificationHandler(@Nullable ReadonlyFragmentModificationHandler readonlyFragmentModificationHandler) {
    impl.settings().setReadOnlyHandler(readonlyFragmentModificationHandler);
  }

  @ApiStatus.Internal
  public boolean isWriteThreadOnly() {
    return impl.settings().isWriteAccessCheckEnabled();
  }

  @ApiStatus.Internal
  public void clearLineModificationFlagsExcept(int @NotNull [] caretLines) {
    impl.mutator().clearLineFlags(0, Integer.MAX_VALUE, caretLines);
  }

  /**
   * NOTE: it is an advanced api, should be used with caution because it breaks the invariant: one text change => one increment
   */
  @ApiStatus.Internal
  public void setModificationStamp(long modificationStamp, boolean incrementModSequence) {
    impl.mutator().setModStamp(modificationStamp, incrementModSequence);
  }

  @ApiStatus.Internal
  public @NotNull FrozenDocument freeze() {
    return (FrozenDocument) impl.frozen();
  }

  @ApiStatus.Internal
  public @NotNull DocumentCore getCore() {
    return impl;
  }

  @TestOnly
  @ApiStatus.Internal
  public boolean stripTrailingSpaces(Project project) {
    return stripTrailingSpaces(project, false);
  }

  @TestOnly
  @ApiStatus.Internal
  public boolean stripTrailingSpaces(Project project, boolean inChangedLinesOnly) {
    return stripTrailingSpaces(project, inChangedLinesOnly, null);
  }

  @TestOnly
  @ApiStatus.Internal
  public int getRangeMarkersSize() {
    return impl.tree().getRangeMarkersSize();
  }

  @TestOnly
  @ApiStatus.Internal
  public int getRangeMarkersNodeSize() {
    return impl.tree().getRangeMarkersNodeSize();
  }

  private @NotNull DocumentImpl hostDocument() {
    return hostDocument != null ? hostDocument : this;
  }

  @Override
  public String toString() {
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(hostDocument());
    return "DocumentImpl[" + (virtualFile == null ? null : virtualFile.getName()) +
           (isInEventsHandling() ? ",inEventHandling" : "") +
           (!isWriteThreadOnly() ? ",nonWriteThreadOnly" : "") +
           (acceptsSlashR() ? ",acceptSlashR" : "") +
           "]";
  }
}
