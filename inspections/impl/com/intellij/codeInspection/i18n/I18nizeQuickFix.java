/**
 * @author cdr
 */
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class I18nizeQuickFix implements LocalQuickFix, I18nQuickFixHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.i18n.I18nizeQuickFix");
  private TextRange mySelectionRange;

  public void applyFix(@NotNull final Project project, final ProblemDescriptor descriptor) {
    // do it later because the fix was called inside writeAction
    ApplicationManager.getApplication().invokeLater(new Runnable(){
      public void run() {
        doFix(descriptor, project);
      }
    });
  }

  @NotNull
  public String getName() {
    return CodeInsightBundle.message("inspection.i18n.quickfix");
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void checkApplicability(final PsiFile psiFile, final Editor editor) throws IncorrectOperationException {
    PsiLiteralExpression literalExpression = I18nizeAction.getEnclosingStringLiteral(psiFile, editor);
    if (literalExpression != null) {
      SelectionModel selectionModel = editor.getSelectionModel();
      if (!selectionModel.hasSelection()) return;
      int start = selectionModel.getSelectionStart();
      int end = selectionModel.getSelectionEnd();
      TextRange textRange = literalExpression.getTextRange();
      if (textRange.contains(start) && textRange.contains(end)) {
        mySelectionRange = new TextRange(start, end);
        return;
      }
    }
    String message = CodeInsightBundle.message("i18nize.error.message");
    throw new IncorrectOperationException(message);
  }

  public void performI18nization(final PsiFile psiFile,
                                 final Editor editor,
                                 PsiLiteralExpression literalExpression,
                                 Collection<PropertiesFile> propertiesFiles,
                                 String key,
                                 String value,
                                 String i18nizedText) throws IncorrectOperationException {
    Project project = psiFile.getProject();
    createProperty(project, propertiesFiles, key, value);
    try {
      final PsiElement newExpression = doReplacementInJava(psiFile, editor,literalExpression, i18nizedText);
      reformatAndCorrectReferences(newExpression);
    }
    catch (IncorrectOperationException e) {
      Messages.showErrorDialog(project, CodeInsightBundle.message("inspection.i18n.expression.is.invalid.error.message"),
                               CodeInsightBundle.message("inspection.error.dialog.title"));
    }
  }

  public I18nizeQuickFixDialog createDialog(PsiFile psiFile, Editor editor, Project project) {
    final PsiLiteralExpression literalExpression = I18nizeAction.getEnclosingStringLiteral(psiFile, editor);
    return createDialog(project, psiFile, literalExpression);
  }

  private void doFix(final ProblemDescriptor descriptor, final Project project) {
    final PsiLiteralExpression literalExpression = (PsiLiteralExpression)descriptor.getPsiElement();
    final PsiFile psiFile = literalExpression.getContainingFile();
    final I18nizeQuickFixDialog dialog = createDialog(project, psiFile, literalExpression);
    dialog.show();
    if (!dialog.isOK()) return;
    final Collection<PropertiesFile> propertiesFiles = dialog.getAllPropertiesFiles();

    if (!CodeInsightUtil.preparePsiElementForWrite(literalExpression)) return;
    for (PropertiesFile file : propertiesFiles) {
      if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    }

    CommandProcessor.getInstance().executeCommand(project, new Runnable(){
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable(){
          public void run() {
            try {
              performI18nization(psiFile, getEditorForFile(psiFile), dialog.getLiteralExpression(), propertiesFiles, dialog.getKey(),
                                 dialog.getValue(), dialog.getI18nizedText());
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      }
    }, CodeInsightBundle.message("quickfix.i18n.command.name"),project);
  }

  private static Editor getEditorForFile(@NotNull final PsiFile psiFile) {
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      PsiFile originalFile = psiFile.getOriginalFile();
      if (originalFile == null) return null;
      virtualFile = originalFile.getVirtualFile();
      if (virtualFile == null) return null;
    }
    final FileEditor[] editors = FileEditorManager.getInstance(psiFile.getProject()).getEditors(virtualFile);
    for (FileEditor editor : editors) {
      if (editor instanceof TextEditor) {
        return ((TextEditor)editor).getEditor();
      }
    }
    return null;
  }

  protected PsiElement doReplacementInJava(@NotNull final PsiFile psiFile,
                                           final Editor editor,
                                           final PsiLiteralExpression literalExpression,
                                           String i18nizedText) throws                                                                                                                            IncorrectOperationException {
    return replaceStringLiteral(literalExpression, i18nizedText);
  }

  private static void reformatAndCorrectReferences(PsiElement newExpression) throws IncorrectOperationException {
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(newExpression.getProject());
    newExpression = codeStyleManager.shortenClassReferences(newExpression);
    codeStyleManager.reformat(newExpression);
  }

  protected I18nizeQuickFixDialog createDialog(final Project project, final PsiFile context, final PsiLiteralExpression literalExpression) {
    String value = (String)literalExpression.getValue();
    if (mySelectionRange != null) {
      TextRange literalRange = literalExpression.getTextRange();
      TextRange intersection = literalRange.intersection(mySelectionRange);
      value = literalExpression.getText().substring(intersection.getStartOffset() - literalRange.getStartOffset(), intersection.getEndOffset() - literalRange.getStartOffset());
    }
    value = value.replaceAll("\n", "\\n");
    return new I18nizeQuickFixDialog(project, context, literalExpression, value, true, true);
  }

  @Nullable public static PsiBinaryExpression breakStringLiteral(PsiLiteralExpression literalExpression, int offset) throws IncorrectOperationException {
    TextRange literalRange = literalExpression.getTextRange();
    PsiElementFactory factory = literalExpression.getManager().getElementFactory();
    if (literalRange.getStartOffset()+1 < offset && offset < literalRange.getEndOffset()-1) {
      PsiBinaryExpression expression = (PsiBinaryExpression)factory.createExpressionFromText("a + b", literalExpression);
      String value = (String)literalExpression.getValue();
      int breakIndex = offset - literalRange.getStartOffset()-1;
      String lsubstring = value.substring(0, breakIndex);
      expression.getLOperand().replace(factory.createExpressionFromText("\""+lsubstring+"\"", literalExpression));
      String rsubstring = value.substring(breakIndex);
      expression.getROperand().replace(factory.createExpressionFromText("\""+rsubstring+"\"", literalExpression));
      PsiBinaryExpression newExpr = (PsiBinaryExpression)literalExpression.replace(expression);
      return newExpr;
    }

    return null;
  }

  private PsiElement replaceStringLiteral(PsiLiteralExpression literalExpression, String i18nizedText) throws IncorrectOperationException {
    PsiElementFactory factory = literalExpression.getManager().getElementFactory();
    if (mySelectionRange != null) {
      try {
        PsiBinaryExpression binaryExpression = breakStringLiteral(literalExpression, mySelectionRange.getEndOffset());
        if (binaryExpression != null) {
          literalExpression = (PsiLiteralExpression)binaryExpression.getLOperand();
        }
        binaryExpression = breakStringLiteral(literalExpression, mySelectionRange.getStartOffset());
        if (binaryExpression != null) {
          literalExpression = (PsiLiteralExpression)binaryExpression.getROperand();
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    PsiExpression expression = factory.createExpressionFromText(i18nizedText, literalExpression);
    return literalExpression.replace(expression);
  }

  public static void createProperty(final Project project,
                                    final Collection<PropertiesFile> propertiesFiles,
                                    final String key,
                                    final String value)
    throws IncorrectOperationException {
    Property property = PropertiesElementFactory.createProperty(project, key, value);
    for (PropertiesFile file : propertiesFiles) {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      documentManager.commitDocument(documentManager.getDocument(file));

      Property existingProperty = file.findPropertyByKey(property.getKey());
      if (existingProperty == null) {
        file.addProperty(property);
      }
    }
  }
}