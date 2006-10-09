package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.impl.ConcatenationToMessageFormatAction;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.OuterLanguageElement;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

public class I18nizeAction extends AnAction implements I18nQuickFixHandler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.i18n.I18nizeAction");

  public void update(AnActionEvent e) {
    boolean active = getHandler(e) != null;
    e.getPresentation().setEnabled(active);
  }

  public I18nQuickFixHandler getHandler(final AnActionEvent e) {
    final Editor editor = getEditor(e);
    if (editor == null) return null;
    PsiFile psiFile = (PsiFile)e.getDataContext().getData(DataConstants.PSI_FILE);
    if (psiFile == null) return null;
    final PsiLiteralExpression literalExpression = getEnclosingStringLiteral(psiFile, editor);
    TextRange range = getSelectedRange(getEditor(e), psiFile);
    PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) return null;
    if (range != null && ConcatenationToMessageFormatAction.getEnclosingLiteralConcatenation(element) != null) {
      return new I18nizeConcatenationQuickFix();
    }
    else if (literalExpression != null && range != null && literalExpression.getTextRange().contains(range)) {
      return new I18nizeQuickFix();
    }
    else if (psiFile instanceof JspFile && range != null) {
      return this;
    }
    else {
      return null;
    }
  }

  public void checkApplicability(final PsiFile psiFile, final Editor editor) throws IncorrectOperationException {
    if (!canBeExtractedAway((JspFile)psiFile, editor)) {
      throw new IncorrectOperationException(CodeInsightBundle.message("i18nize.jsp.error"));
    }
  }

  public void performI18nization(final PsiFile psiFile,
                                 final Editor editor,
                                 PsiLiteralExpression literalExpression,
                                 Collection<PropertiesFile> propertiesFiles,
                                 String key,
                                 String value,
                                 String i18nizedText) throws IncorrectOperationException {
    Project project = psiFile.getProject();
    TextRange selectedText = getSelectedRange(editor, psiFile);
    if (selectedText == null) return;
    I18nizeQuickFix.createProperty(project, propertiesFiles, key, value);
    editor.getDocument().replaceString(selectedText.getStartOffset(), selectedText.getEndOffset(), i18nizedText);
  }

  public static PsiLiteralExpression getEnclosingStringLiteral(final PsiFile psiFile, final Editor editor) {
    PsiElement psiElement = psiFile.findElementAt(editor.getCaretModel().getOffset());
    if (psiElement == null) return null;
    PsiLiteralExpression expression = PsiTreeUtil.getParentOfType(psiElement, PsiLiteralExpression.class);
    if (expression == null || !(expression.getValue() instanceof String)) return null;
    return expression;
  }

  private static boolean canBeExtractedAway(final JspFile jspFile, final Editor editor) {
    final TextRange selectedRange=getSelectedRange(editor, jspFile);
    // must contain no or balanced tags only
    // must not contain scriptlets or custom tags
    final Ref<Boolean> result = new Ref<Boolean>(Boolean.TRUE);
    PsiFile root = jspFile.getBaseLanguageRoot();
    root.accept(new PsiRecursiveElementVisitor(){
      public void visitElement(PsiElement element) {
        TextRange elementRange = element.getTextRange();
        if (elementRange.intersectsStrict(selectedRange)) {
          if (element instanceof OuterLanguageElement ||
              element instanceof XmlTag
              && !selectedRange.contains(elementRange)
              && (!elementRange.contains(selectedRange) || !((XmlTag)element).getValue().getTextRange().contains(selectedRange))) {
            result.set(Boolean.FALSE);
            return;
          }
        }
        super.visitElement(element);
      }
    });
    return result.get().booleanValue();
  }

  @Nullable private static TextRange getSelectedRange(Editor editor, final PsiFile psiFile) {
    if (editor == null) return null;
    String selectedText = editor.getSelectionModel().getSelectedText();
    if (selectedText != null) {
      return new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());
    }
    PsiElement psiElement = psiFile.findElementAt(editor.getCaretModel().getOffset());
    if (psiElement==null || psiElement instanceof PsiWhiteSpace) return null;
    return psiElement.getTextRange();
  }

  private static Editor getEditor(final AnActionEvent e) {
    return (Editor)e.getDataContext().getData(DataConstants.EDITOR);
  }

  public void actionPerformed(AnActionEvent e) {
    final Editor editor = getEditor(e);
    final Project project = editor.getProject();
    final PsiFile psiFile = (PsiFile)e.getDataContext().getData(DataConstants.PSI_FILE);
    if (psiFile == null) return;
    final I18nQuickFixHandler handler = getHandler(e);
    if (handler == null) return;
    try {
      handler.checkApplicability(psiFile, editor);
    }
    catch (IncorrectOperationException ex) {
      JOptionPane.showMessageDialog(null, ex.getMessage(), CodeInsightBundle.message("i18nize.error.title"), JOptionPane.ERROR_MESSAGE);
      return;
    }

    final I18nizeQuickFixDialog dialog = handler.createDialog(psiFile, editor, project);
    dialog.show();
    if (!dialog.isOK()) return;

    if (!CodeInsightUtil.prepareFileForWrite(psiFile)) return;
    final Collection<PropertiesFile> propertiesFiles = dialog.getAllPropertiesFiles();
    for (PropertiesFile file : propertiesFiles) {
      if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable(){
      public void run() {
        CommandProcessor.getInstance().executeCommand(project, new Runnable(){
          public void run() {
            try {
              handler.performI18nization(psiFile, editor, dialog.getLiteralExpression(), propertiesFiles, dialog.getKey(), dialog.getValue(), dialog.getI18nizedText());
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }, CodeInsightBundle.message("quickfix.i18n.command.name"),project);
      }
    });
  }

  public I18nizeQuickFixDialog createDialog(final PsiFile psiFile, final Editor editor, final Project project) {
    JspFile jspFile = (JspFile)psiFile;

    TextRange selectedRange = getSelectedRange(editor, psiFile);
    if (selectedRange == null) return null;
    String text = editor.getDocument().getText().substring(selectedRange.getStartOffset(), selectedRange.getEndOffset());
    return new I18nizeQuickFixDialog(project, jspFile, null, text, false, true){
      protected String getTemplateName() {
        return FileTemplateManager.TEMPLATE_I18NIZED_JSP_EXPRESSION;
      }
    };
  }

}
