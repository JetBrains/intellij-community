// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.text.ImmutableCharSequence;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.List;

@ApiStatus.Internal
public final class CompletionAssertions {

  @ApiStatus.Internal
  public static void checkEditorValid(Editor editor) {
    if (!isEditorValid(editor)) {
      throw new AssertionError();
    }
  }

  @ApiStatus.Internal
  public static boolean isEditorValid(Editor editor) {
    return !(editor instanceof EditorWindow editorWindow) || editorWindow.isValid();
  }

  private static Attachment createAstAttachment(PsiFile fileCopy, final PsiFile originalFile) {
    return new Attachment(originalFile.getViewProvider().getVirtualFile().getPath() + " syntactic tree.txt", DebugUtil.psiToString(fileCopy,
                                                                                                                                   true, true));
  }

  private static Attachment createFileTextAttachment(PsiFile fileCopy, final PsiFile originalFile) {
    return new Attachment(originalFile.getViewProvider().getVirtualFile().getPath(), fileCopy.getText());
  }

  static void assertInjectedOffsets(int hostStartOffset, PsiFile injected, DocumentWindow documentWindow) {
    assert documentWindow != null : "no DocumentWindow for an injected fragment";

    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(injected.getProject());
    TextRange injectedRange = injected.getTextRange();
    int hostMinOffset = injectedLanguageManager.injectedToHost(injected, injectedRange.getStartOffset(), true);
    int hostMaxOffset = injectedLanguageManager.injectedToHost(injected, injectedRange.getEndOffset(), false);
    assert hostStartOffset >= hostMinOffset : "startOffset before injected";
    assert hostStartOffset <= hostMaxOffset : "startOffset after injected";
  }

  static void assertHostInfo(PsiFile hostCopy, OffsetMap hostMap) {
    PsiUtilCore.ensureValid(hostCopy);
    if (hostMap.getOffset(CompletionInitializationContext.START_OFFSET) > hostCopy.getTextLength()) {
      throw new AssertionError("startOffset outside the host file: " + hostMap.getOffset(CompletionInitializationContext.START_OFFSET) + "; " + hostCopy);
    }
  }

  @Contract("_,_,_,null->fail")
  static void assertCompletionPositionPsiConsistent(OffsetsInFile offsets,
                                                    int offset,
                                                    PsiFile originalFile, PsiElement insertedElement) {
    PsiFile fileCopy = offsets.getFile();
    if (insertedElement == null) {
      throw new RuntimeExceptionWithAttachments(
        "No element at insertion offset",
        "offset=" + offset,
        createFileTextAttachment(fileCopy, originalFile),
        createAstAttachment(fileCopy, originalFile));
    }

    final TextRange range = insertedElement.getTextRange();
    CharSequence fileCopyText = fileCopy.getViewProvider().getContents();
    if ((range.getEndOffset() > fileCopyText.length()) ||
        !isEquals(fileCopyText.subSequence(range.getStartOffset(), range.getEndOffset()),
                  insertedElement.getNode().getChars())) {
      throw new RuntimeExceptionWithAttachments(
        "Inconsistent completion tree",
        "range=" + range + "; fileLength=" + fileCopyText.length(),
        createFileTextAttachment(fileCopy, originalFile),
        createAstAttachment(fileCopy, originalFile),
        new Attachment("Element at caret.txt", insertedElement.getText()));
    }
  }

  private static boolean isEquals(CharSequence left, CharSequence right) {
    if (left == right) return true;
    if (left instanceof ImmutableCharSequence && right instanceof ImmutableCharSequence) {
      return left.equals(right);
    }
    return left.toString().equals(right.toString());
  }

  static void assertCorrectOriginalFile(@NonNls String prefix, PsiFile file, PsiFile copy) {
    if (copy.getOriginalFile() != file) {
      throw new AssertionError(prefix + " copied file doesn't have correct original: noOriginal=" + (copy.getOriginalFile() == copy) +
                               "\n file " + fileInfo(file) +
                               "\n copy " + fileInfo(copy));
    }
  }

  private static @NonNls String fileInfo(PsiFile file) {
    return file + " of " + file.getClass() +
           " in " + file.getViewProvider() + ", languages=" + file.getViewProvider().getLanguages() +
           ", physical=" + file.isPhysical();
  }

  @ApiStatus.Internal
  public static final class WatchingInsertionContext extends InsertionContext implements Disposable {
    private RangeMarkerEx tailWatcher;
    Throwable invalidateTrace;
    DocumentEvent killer;
    private RangeMarkerSpy spy;

    WatchingInsertionContext(OffsetMap offsetMap, PsiFile file, char completionChar, List<LookupElement> items, Editor editor) {
      super(offsetMap, completionChar, items.toArray(LookupElement.EMPTY_ARRAY),
            file, editor,
            shouldAddCompletionChar(completionChar));
    }

    @ApiStatus.Internal
    public Throwable getInvalidateTrace() {
      return invalidateTrace;
    }

    @Override
    public void setTailOffset(int offset) {
      super.setTailOffset(offset);
      watchTail(offset);
    }

    private void watchTail(int offset) {
      stopWatching();
      tailWatcher = (RangeMarkerEx)getDocument().createRangeMarker(offset, offset);
      if (!tailWatcher.isValid()) {
        throw new AssertionError(getDocument() + "; offset=" + offset);
      }
      tailWatcher.setGreedyToRight(true);
      spy = new RangeMarkerSpy(this, tailWatcher);
      getDocument().addDocumentListener(spy);
    }

    @ApiStatus.Internal
    public void stopWatching() {
      if (tailWatcher != null) {
        if (spy != null) {
          getDocument().removeDocumentListener(spy);
          spy = null;
        }
        tailWatcher.dispose();
      }
    }

    @Override
    public int getTailOffset() {
      if (!getOffsetMap().containsOffset(TAIL_OFFSET) && invalidateTrace != null) {
        throw new RuntimeExceptionWithAttachments("Tail offset invalid", new Attachment("invalidated", invalidateTrace));
      }

      int offset = super.getTailOffset();
      if (tailWatcher.getStartOffset() != tailWatcher.getEndOffset() && offset > 0) {
        watchTail(offset);
      }

      return offset;
    }

    @Override
    public void dispose() {
      stopWatching(); // used by Fleet as ad-hoc memory leak fix
    }
  }

  private static class RangeMarkerSpy implements DocumentListener {
    // Do not leak the whole InsertionContext via DocumentListener.
    private final WeakReference<WatchingInsertionContext> myContextRef;
    private final RangeMarker myMarker;

    RangeMarkerSpy(@NotNull WatchingInsertionContext context, @NotNull RangeMarker marker) {
      myContextRef = new WeakReference<>(context);
      myMarker = marker;
      assert myMarker.isValid();
    }

    protected void invalidated(@NotNull DocumentEvent e) {
      WatchingInsertionContext context = myContextRef.get();
      if (context != null && context.invalidateTrace == null) {
        context.invalidateTrace = new Throwable();
        context.killer = e;
      }
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent e) {
      if (!myMarker.isValid()) {
        invalidated(e);
      }
    }
  }
}
