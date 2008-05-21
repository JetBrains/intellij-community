package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.lang.LanguageSurrounders;
import com.intellij.lang.Language;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

public class SurroundWithHandler implements CodeInsightActionHandler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler");
  private static final String CHOOSER_TITLE = CodeInsightBundle.message("surround.with.chooser.title");

  public void invoke(final Project project, final Editor editor, PsiFile file){
    invoke(project, editor, file, null);
  }

  public boolean startInWriteAction() {
    return true;
  }

  public static void invoke(final Project project, final Editor editor, PsiFile file, Surrounder surrounder){
    if (!editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().selectLineAtCaret();
    }
    int startOffset = editor.getSelectionModel().getSelectionStart();
    int endOffset = editor.getSelectionModel().getSelectionEnd();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement element1 = file.findElementAt(startOffset);
    PsiElement element2 = file.findElementAt(endOffset - 1);

    if (element1 == null || element2 == null) return;

    TextRange textRange = new TextRange(startOffset, endOffset);
    for(SurroundWithRangeAdjuster adjuster: Extensions.getExtensions(SurroundWithRangeAdjuster.EP_NAME)) {
      textRange = adjuster.adjustSurroundWithRange(file, textRange);
      if (textRange == null) return;
    }
    startOffset = textRange.getStartOffset();
    endOffset = textRange.getEndOffset();
    element1 = file.findElementAt(startOffset);

    final Language baseLanguage = file.getViewProvider().getBaseLanguage();
    final Language l = element1.getParent().getLanguage();
    List<SurroundDescriptor> surroundDescriptors;

    surroundDescriptors = LanguageSurrounders.INSTANCE.allForLanguage(l);
    if (l != baseLanguage) surroundDescriptors.addAll(LanguageSurrounders.INSTANCE.allForLanguage(baseLanguage));

    if (surroundDescriptors.isEmpty()) return;

    for (SurroundDescriptor descriptor : surroundDescriptors) {
      final PsiElement[] elements = descriptor.getElementsToSurround(file, startOffset, endOffset);
      if (elements.length > 0) {
        if (surrounder == null) {
          PopupActionChooser popupActionChooser = new PopupActionChooser(CHOOSER_TITLE);
          popupActionChooser.invoke(project, editor, descriptor.getSurrounders(), elements);
          if (popupActionChooser.isHasEnabledSurrounders()) return;
        }
        else {
          if (surroundDescriptors.size() == 1) {
            doSurround(project, editor, surrounder, elements);
          } else {
            invokeSurrondInTestMode(project, editor, surrounder, descriptor, elements);
          }
        }
      }
    }
  }

  @TestOnly
  private static void invokeSurrondInTestMode(final Project project, final Editor editor, final Surrounder surrounder,
                                              final SurroundDescriptor descriptor, final PsiElement[] elements) {
    for (final Surrounder surrounder1 : descriptor.getSurrounders()) {
      if (surrounder1.getClass().equals(surrounder.getClass())) {
        doSurround(project, editor, surrounder, elements);
        return;
      }
    }
  }

  static void doSurround(final Project project, final Editor editor, final Surrounder surrounder, final PsiElement[] elements) {
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (!file.isWritable()){
      if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(editor.getDocument(), project)){
        return;
      }
    }

    try {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      int col = editor.getCaretModel().getLogicalPosition().column;
      int line = editor.getCaretModel().getLogicalPosition().line;
      LogicalPosition pos = new LogicalPosition(0, 0);
      editor.getCaretModel().moveToLogicalPosition(pos);
      TextRange range = surrounder.surroundElements(project, editor, elements);
      if (TemplateManager.getInstance(project).getActiveTemplate(editor) == null) {
        LogicalPosition pos1 = new LogicalPosition(line, col);
        editor.getCaretModel().moveToLogicalPosition(pos1);
      }
      if (range != null) {
        int offset = range.getStartOffset();
        editor.getCaretModel().moveToOffset(offset);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
