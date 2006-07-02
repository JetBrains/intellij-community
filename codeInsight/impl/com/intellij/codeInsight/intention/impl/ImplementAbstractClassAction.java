/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 26, 2002
 * Time: 2:33:58 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

public class ImplementAbstractClassAction implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.ImplementAbstractClassAction");
  private String myText = CodeInsightBundle.message("intention.implement.abstract.class.default.text");
  private static final @NonNls String IMPL_SUFFIX = "Impl";

  public String getText() {
    return myText;
  }

  public String getFamilyName() {
    return CodeInsightBundle.message("intention.implement.abstract.class.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return false;
    PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass == null || psiClass.isAnnotationType() || psiClass.isEnum()) return false;
    PsiJavaToken lBrace = psiClass.getLBrace();
    if (lBrace == null) return false;
    if (element.getTextOffset() >= lBrace.getTextOffset()) return false;
    if (!psiClass.isInterface() && !psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) return false;

    myText = psiClass.isInterface() ?
             CodeInsightBundle.message("intention.implement.abstract.class.interface.text") :
             CodeInsightBundle.message("intention.implement.abstract.class.default.text");
    return true;
  }

  public void invoke(final Project project, Editor editor, final PsiFile file) throws IncorrectOperationException {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);

    PsiFile sourceFile = psiClass.getContainingFile();
    PsiDirectory sourceDir = sourceFile.getContainingDirectory();

    final PsiPackage aPackage = sourceDir.getPackage();
    final CreateClassDialog dialog = new CreateClassDialog(
      project,
      myText,
      psiClass.getName() + IMPL_SUFFIX,
      aPackage != null ? aPackage.getQualifiedName() : "",
      CreateClassKind.CLASS.getDescription(), true);
    dialog.show();
    if (!dialog.isOK()) return;
    final PsiDirectory targetDirectory = dialog.getTargetDirectory();
    if (targetDirectory == null) return;

    PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Runnable () {
      public void run() {
        PsiClass targetClass = ApplicationManager.getApplication().runWriteAction(new Computable<PsiClass>() {
          public PsiClass compute() {

            IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

            PsiClass targetClass;
            try {
              targetClass = targetDirectory.createClass(dialog.getClassName());
            }
            catch (IncorrectOperationException e) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                      public void run() {
                        Messages.showErrorDialog(project,
                                                 CodeInsightBundle.message( "intention.implement.abstract.class.error.cannot.create.class.message", dialog.getClassName()),
                                                 CodeInsightBundle.message("intention.implement.abstract.class.error.cannot.create.class.title"));
                      }
                    });
              return null;
            }
            PsiJavaCodeReferenceElement ref = file.getManager().getElementFactory().createClassReferenceElement(psiClass);

            try {
              if (psiClass.isInterface()) {
                targetClass.getImplementsList().add(ref);
              }
              else {
                targetClass.getExtendsList().add(ref);
              }
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }

            return targetClass;
          }
        });
        if (targetClass == null) return;

        final Editor editor1 = CodeInsightUtil.positionCursor(project, targetClass.getContainingFile(), targetClass.getLBrace());
        if (editor1 == null) return;
        OverrideImplementUtil.chooseAndImplementMethods(project, editor1, targetClass);
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }

}
