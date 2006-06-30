package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.IncorrectOperationException;

public class SurroundWithHandler implements CodeInsightActionHandler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler");
  private static final String CHOOSER_TITLE = CodeInsightBundle.message("surround.with.chooser.title");

  public void invoke(final Project project, final Editor editor, PsiFile file){
    invoke(project, editor, file, null);
  }

  public boolean startInWriteAction() {
    return true;
  }

  public static boolean isLanguageWithWSSignificant(PsiElement element) {
    return isLanguageWithWSSignificant(getLanguage(element));
  }

  public static boolean isLanguageWithWSSignificant(Language lang) {
    return lang == StdLanguages.HTML ||
           lang == StdLanguages.XHTML ||
           lang == StdLanguages.JSP ||
           lang == StdLanguages.JSPX;
  }

  private static Language getLanguage(PsiElement element) {
    Language lang = element.getLanguage();
    if (lang == StdLanguages.XML) lang = element.getParent().getLanguage();
    return lang;
  }

  public void invoke(final Project project, final Editor editor, PsiFile file, Surrounder surrounder){
    if (!file.isWritable()){
      if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(editor.getDocument(), project)){
        return;
      }
    }

    if (!editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().selectLineAtCaret();
    }
    int startOffset = editor.getSelectionModel().getSelectionStart();
    int endOffset = editor.getSelectionModel().getSelectionEnd();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement element1 = file.findElementAt(startOffset);
    PsiElement element2 = file.findElementAt(endOffset - 1);

    if (element1 == null || element2 == null) return;
    Language lang = getLanguage(element1);
    Language lang2 = getLanguage(element2);

    if (element1 instanceof PsiWhiteSpace && isLanguageWithWSSignificant(lang) ) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.findElementAt(startOffset);
    }
    if (element2 instanceof PsiWhiteSpace && isLanguageWithWSSignificant(lang2) ) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.findElementAt(endOffset);
    }

    lang = getLanguage(element1);
    lang2 = getLanguage(element2);

    if(lang != lang2) return;

    final SurroundDescriptor[] surroundDescriptors = element1.getLanguage().getSurroundDescriptors();
    if (surroundDescriptors.length == 0) return;

    for (SurroundDescriptor descriptor : surroundDescriptors) {
      final PsiElement[] elements = descriptor.getElementsToSurround(file, startOffset, endOffset);
      if (elements.length > 0) {
        if (surrounder == null) {
          PopupActionChooser popupActionChooser = new PopupActionChooser(CHOOSER_TITLE);
          popupActionChooser.invoke(project, editor, descriptor.getSurrounders(), elements);
          return;
        }
        else {
          doSurround(project, editor, surrounder, elements);
        }
      }
    }
  }

  static void doSurround(final Project project, final Editor editor, final Surrounder surrounder, final PsiElement[] elements) {
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