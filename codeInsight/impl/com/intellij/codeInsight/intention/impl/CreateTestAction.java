package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightNamesUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CreateTestAction extends PsiElementBaseIntentionAction {
  @NotNull
  public String getText() {
    return CodeInsightBundle.message("intention.create.test");
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, @Nullable PsiElement element) {
    if (element == null) return false;

    PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass == null ||
        psiClass.isAnnotationType() ||
        psiClass.isInterface() ||
        psiClass.isEnum() ||
        psiClass instanceof PsiAnonymousClass) {
      return false;
    }

    PsiJavaToken leftBrace = psiClass.getLBrace();
    if (leftBrace == null) return false;
    if (element.getTextOffset() >= leftBrace.getTextOffset()) return false;

    TextRange declarationRange = HighlightNamesUtil.getClassDeclarationTextRange(psiClass);
    if (!declarationRange.contains(element.getTextRange())) return false;

    return true;
  }

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());

    final Module srcModule = ModuleUtil.findModuleForPsiElement(file);
    final PsiClass srcClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    PsiDirectory srcDir = file.getContainingDirectory();
    PsiPackage srcPackage = JavaDirectoryService.getInstance().getPackage(srcDir);

    ensureJuntLibraryAttached(project, srcModule);

    CreateTestDialog d = new CreateTestDialog(project, getText(), srcClass.getName() + "Test", srcPackage, srcModule);
    d.show();
    if (!d.isOK()) return;

    final String targetClassName = d.getClassName();
    final String superClassName = d.getSuperClassName();
    final PsiDirectory targetDirectory = d.getTargetDirectory();

    if (targetDirectory == null) return;

    PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

              PsiClass targetClass = JavaDirectoryService.getInstance().createClass(targetDirectory, targetClassName);
              addSuperClass(targetClass, project, superClassName);

              CodeInsightUtil.positionCursor(project, targetClass.getContainingFile(), targetClass.getLBrace());
            }
            catch (IncorrectOperationException e) {
              showErrorLater(project, targetClassName);
            }
          }
        });
      }
    });
  }

  private void ensureJuntLibraryAttached(Project project, final Module srcModule) {
    if (findClass(project, "junit.framework.TestCase") != null) return;

    int result = Messages.showYesNoDialog("You don't have JUnit library attached to the module.\n" +
                                          "Do you want to add it?",
                                          "Create Test", Messages.getQuestionIcon());
    if (result != 0) return;

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        OrderEntryFix.addJarToRoots(JavaSdkUtil.getJunit4JarPath(), srcModule);
      }
    });
  }

  private void addSuperClass(PsiClass targetClass, Project project, String superClassName) throws IncorrectOperationException {
    if (superClassName == null) return;

    PsiElementFactory ef = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiJavaCodeReferenceElement superClassRef;

    PsiClass superClass = findClass(project, superClassName);
    if (superClass != null) {
      superClassRef = ef.createClassReferenceElement(superClass);
    }
    else {
      superClassRef = ef.createFQClassNameReferenceElement(superClassName, GlobalSearchScope.allScope(project));
    }
    targetClass.getExtendsList().add(superClassRef);
  }

  private PsiClass findClass(Project project, String fqName) {
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    return JavaPsiFacade.getInstance(project).findClass(fqName, scope);
  }

  private void showErrorLater(final Project project, final String targetClassName) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        Messages.showErrorDialog(project,
                                 CodeInsightBundle.message("intention.error.cannot.create.class.message", targetClassName),
                                 CodeInsightBundle.message("intention.error.cannot.create.class.title"));
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }
}