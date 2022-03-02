// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.PasteProvider;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.EditorTextInsertHandler;
import com.intellij.openapi.editor.actions.BasePasteHandler;
import com.intellij.openapi.editor.actions.PasteAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.util.Producer;
import com.intellij.util.SlowOperations;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PasteHandler extends EditorActionHandler implements EditorTextInsertHandler {
  private static final ExtensionPointName<PasteProvider> EP_NAME = ExtensionPointName.create("com.intellij.customPasteProvider");

  private final EditorActionHandler myOriginalHandler;

  public PasteHandler(EditorActionHandler originalAction) {
    myOriginalHandler = originalAction;
  }

  @Override
  public void doExecute(@NotNull final Editor editor, Caret caret, final DataContext dataContext) {
    assert caret == null : "Invocation of 'paste' operation for specific caret is not supported";
    execute(editor, dataContext, null);
  }

  private static Transferable getContentsToPasteToEditor(@Nullable Producer<? extends Transferable> producer) {
    if (producer == null) {
      return CopyPasteManager.getInstance().getContents();
    }
    else {
      return producer.produce();
    }
  }

  @Override
  public void execute(@NotNull Editor editor, DataContext dataContext, @Nullable Producer<? extends Transferable> producer) {
    final Transferable transferable = getContentsToPasteToEditor(producer);
    if (transferable == null) return;

    if (!EditorModificationUtil.checkModificationAllowed(editor)) return;

    final Document document = editor.getDocument();
    if (!EditorModificationUtil.requestWriting(editor)) {
      return;
    }

    DataContext context = dataId -> {
      return PasteAction.TRANSFERABLE_PROVIDER.is(dataId) ? (Producer<Transferable>)() -> transferable : dataContext.getData(dataId);
    };

    final Project project = editor.getProject();
    if (project == null || editor.isColumnMode() || editor.getCaretModel().getCaretCount() > 1) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, null, context);
      }
      return;
    }

    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, null, context);
      }
      return;
    }

    DumbService.getInstance(project).runWithAlternativeResolveEnabled(() -> {
      document.startGuardedBlockChecking();
      try {
        for (PasteProvider provider : EP_NAME.getExtensionList()) {
          if (provider.isPasteEnabled(context)) {
            provider.performPaste(context);
            return;
          }
        }
        doPaste(editor, project, file, document, transferable);
      }
      catch (ReadOnlyFragmentModificationException e) {
        EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(document).handle(e);
      }
      finally {
        document.stopGuardedBlockChecking();
      }
    });
  }

  private static void doPaste(@NotNull final Editor editor,
                              @NotNull final Project project,
                              final PsiFile file,
                              final Document document,
                              @NotNull final Transferable content) {
    final TypingActionsExtension typingActionsExtension = TypingActionsExtension.findForContext(project, editor);
    try {
      typingActionsExtension.startPaste(project, editor);
      doPasteAction(editor, project, file, document, content, typingActionsExtension);
    }
    finally {
      typingActionsExtension.endPaste(project, editor);
    }
  }

  private static class ProcessorAndData<Data extends TextBlockTransferableData> {
    final CopyPastePostProcessor<Data> processor;
    final List<Data> data;

    private ProcessorAndData(@NotNull CopyPastePostProcessor<Data> processor, @NotNull List<Data> data) {
      this.processor = processor;
      this.data = data;
    }

    void process(Project project, Editor editor, RangeMarker bounds, int caretOffset, Ref<Boolean> skipIndentation) {
      processor.processTransferableData(project, editor, bounds, caretOffset, skipIndentation, data);
    }

    static <T extends TextBlockTransferableData> @Nullable ProcessorAndData<T> create(
      @NotNull CopyPastePostProcessor<T> processor,
      @NotNull Transferable content
    ) {
      List<T> data = processor.extractTransferableData(content);
      if (data.isEmpty()) return null;
      return new ProcessorAndData<>(processor, data);
    }
  }

  private static void doPasteAction(final Editor editor,
                                    final Project project,
                                    final PsiFile file,
                                    final Document document,
                                    @NotNull final Transferable content,
                                    @NotNull final TypingActionsExtension typingActionsExtension) {
    CopyPasteManager.getInstance().stopKillRings();

    String text = null;
    try {
      text = (String)content.getTransferData(DataFlavor.stringFlavor);
    }
    catch (Exception e) {
      editor.getComponent().getToolkit().beep();
    }
    if (text == null) return;
    int textLength = text.length();
    if (BasePasteHandler.isContentTooLarge(textLength)) {
      BasePasteHandler.contentLengthLimitExceededMessage(textLength);
      return;
    }

    final CodeInsightSettings settings = CodeInsightSettings.getInstance();

    final List<ProcessorAndData<?>> extraData = new ArrayList<>();
    final Collection<TextBlockTransferableData> allValues = new ArrayList<>();

    for (CopyPastePostProcessor<? extends TextBlockTransferableData> processor : CopyPastePostProcessor.EP_NAME.getExtensionList()) {
      ProcessorAndData<? extends TextBlockTransferableData> data = ProcessorAndData.create(processor, content);
      if (data != null) {
        extraData.add(data);
        allValues.addAll(data.data);
      }
    }

    text = TextBlockTransferable.convertLineSeparators(editor, text, allValues);

    final CaretModel caretModel = editor.getCaretModel();
    final SelectionModel selectionModel = editor.getSelectionModel();
    final int col = caretModel.getLogicalPosition().column;

    // There is a possible case that we want to perform paste while there is an active selection at the editor and caret is located
    // inside it (e.g. Ctrl+A is pressed while caret is not at the zero column). We want to insert the text at selection start column
    // then, hence, inserted block of text should be indented according to the selection start as well.
    final int blockIndentAnchorColumn;
    final int caretOffset = caretModel.getOffset();
    if (selectionModel.hasSelection() && caretOffset >= selectionModel.getSelectionStart()) {
      blockIndentAnchorColumn = editor.offsetToLogicalPosition(selectionModel.getSelectionStart()).column;
    }
    else {
      blockIndentAnchorColumn = col;
    }

    // We assume that EditorModificationUtil.insertStringAtCaret() is smart enough to remove currently selected text (if any).

    RawText rawText = RawText.fromTransferable(content);
    String newText = text;
    for (CopyPastePreProcessor preProcessor : CopyPastePreProcessor.EP_NAME.getExtensionList()) {
      newText = preProcessor.preprocessOnPaste(project, file, editor, newText, rawText);
    }

    final boolean pastedTextWasChanged = !text.equals(newText);
    int indentOptions = pastedTextWasChanged ? CodeInsightSettings.REFORMAT_BLOCK : settings.REFORMAT_ON_PASTE;
    text = newText;

    if (LanguageFormatting.INSTANCE.forContext(file) == null && indentOptions != CodeInsightSettings.NO_REFORMAT) {
      indentOptions = CodeInsightSettings.INDENT_BLOCK;
    }

    final String _text = text;
    ApplicationManager.getApplication().runWriteAction(() -> {
      EditorModificationUtilEx.insertStringAtCaret(editor, _text, false, true);
      if (!project.isDisposed()) {
        ((UndoManagerImpl)UndoManager.getInstance(project)).addDocumentAsAffected(editor.getDocument());
      }
    });

    int length = text.length();
    int offset = caretModel.getOffset() - length;
    if (offset < 0) {
      length += offset;
      offset = 0;
    }
    final RangeMarker bounds = document.createRangeMarker(offset, offset + length);

    caretModel.moveToOffset(bounds.getEndOffset());
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    selectionModel.removeSelection();

    // `skipIndentation` is additionally used as marker for changed pasted test
    // Any value, except `null` is a signal that the text was transformed.
    // For the `CopyPasteFoldingProcessor` it means that folding data is not valid and cannot be applied.
    final Ref<Boolean> skipIndentation = new Ref<>(pastedTextWasChanged ? Boolean.FALSE : null);
    for (ProcessorAndData<?> data : extraData) {
      SlowOperations.allowSlowOperations(() -> {
        data.process(project, editor, bounds, caretOffset, skipIndentation);
      });
    }

    boolean pastedTextContainsWhiteSpacesOnly =
      CharArrayUtil.shiftForward(document.getCharsSequence(), bounds.getStartOffset(), " \n\t") >= bounds.getEndOffset();

    VirtualFile virtualFile = file.getVirtualFile();
    if (!pastedTextContainsWhiteSpacesOnly &&
        (virtualFile == null || !SingleRootFileViewProvider.isTooLargeForIntelligence(virtualFile))) {
      final int howtoReformat =
        skipIndentation.get() == Boolean.TRUE
        && (indentOptions == CodeInsightSettings.INDENT_BLOCK || indentOptions == CodeInsightSettings.INDENT_EACH_LINE)
        ? CodeInsightSettings.NO_REFORMAT
        : indentOptions;
      ApplicationManager.getApplication().runWriteAction(
        () -> typingActionsExtension
          .format(project, editor, howtoReformat, bounds.getStartOffset(), bounds.getEndOffset(), blockIndentAnchorColumn, true)
      );
    }

    if (bounds.isValid()) {
      caretModel.moveToOffset(bounds.getEndOffset());
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      selectionModel.removeSelection();
      editor.putUserData(EditorEx.LAST_PASTED_REGION, TextRange.create(bounds));
    }
  }
}
