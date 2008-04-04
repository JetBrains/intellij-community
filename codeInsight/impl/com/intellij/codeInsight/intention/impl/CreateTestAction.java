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
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

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

    CreateTestDialog d = new CreateTestDialog(project,
                                              getText(),
                                              srcClass.getName() + "Test",
                                              hasJUnitLib(project) ? "junit.framework.TestCase" : "",
                                              srcPackage,
                                              srcModule);
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
    if (hasJUnitLib(project) || hasTestNGLib(project)) return;

    JLabel label = new JLabel(CodeInsightBundle.message("intention.create.test.no.testing.library"));

    JRadioButton junit3Button = new JRadioButton("JUnit3");
    JRadioButton junit4Button = new JRadioButton("JUnit4");
    JRadioButton testngButton = new JRadioButton("TestNG");
    ButtonGroup group = new ButtonGroup();
    group.add(junit3Button);
    group.add(junit4Button);
    group.add(testngButton);
    junit3Button.setSelected(true);

    testngButton.setEnabled(getTestNGJarPath() != null);

    JPanel buttonsPanel = new JPanel();
    BoxLayout l = new BoxLayout(buttonsPanel, BoxLayout.Y_AXIS);
    buttonsPanel.setLayout(l);
    buttonsPanel.add(junit3Button);
    buttonsPanel.add(junit4Button);
    buttonsPanel.add(testngButton);

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(label, BorderLayout.NORTH);
    panel.add(buttonsPanel, BorderLayout.CENTER);

    DialogBuilder builder = new DialogBuilder(project);
    builder.setTitle(getText());
    builder.setCenterPanel(panel);
    int result = builder.show();

    if (result != 0) return;

    final String[] path = new String[1];
    if (junit3Button.isSelected()) path[0] = JavaSdkUtil.getJunit3JarPath();
    if (junit4Button.isSelected()) path[0] = JavaSdkUtil.getJunit4JarPath();
    if (testngButton.isSelected()) path[0] = getTestNGJarPath();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        OrderEntryFix.addJarToRoots(path[0], srcModule);
      }
    });
  }

  private boolean hasJUnitLib(Project project) {
    return findClass(project, "junit.framework.TestCase") != null;
  }

  private boolean hasTestNGLib(Project project) {
    return findClass(project, "org.testng.annotations.Test") != null;
  }

  private static String getTestNGJarPath() {
    try {
      return PathUtil.getJarPathForClass(Class.forName("org.testng.annotations.Test"));
    }
    catch (ClassNotFoundException e) {
      return null;
    }
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