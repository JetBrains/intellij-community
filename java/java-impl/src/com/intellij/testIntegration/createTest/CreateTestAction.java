/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testIntegration.createTest;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightNamesUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testIntegration.TestFrameworkDescriptor;
import com.intellij.testIntegration.TestIntegrationUtils;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class CreateTestAction extends PsiElementBaseIntentionAction {
  @NotNull
  public String getText() {
    return CodeInsightBundle.message("intention.create.test");
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (Extensions.getExtensions(TestFrameworkDescriptor.EXTENSION_NAME).length == 0) return false;

    if (!isAvailableForElement(element)) return false;

    PsiClass psiClass = getContainingClass(element);

    PsiJavaToken leftBrace = psiClass.getLBrace();
    if (leftBrace == null) return false;
    if (element.getTextOffset() >= leftBrace.getTextOffset()) return false;

    TextRange declarationRange = HighlightNamesUtil.getClassDeclarationTextRange(psiClass);
    if (!declarationRange.contains(element.getTextRange())) return false;

    return true;
  }

  public boolean isAvailableForElement(PsiElement element) {
    if (element == null) return false;
    
    PsiClass psiClass = getContainingClass(element);

    if (psiClass == null) return false;

    Module srcModule = ModuleUtil.findModuleForPsiElement(psiClass);
    if (srcModule == null) return false;

    if (psiClass.isAnnotationType() ||
        psiClass.isInterface() ||
        psiClass.isEnum() ||
        psiClass instanceof PsiAnonymousClass ||
        PsiTreeUtil.getParentOfType(psiClass, PsiClass.class) != null || // inner
        isUnderTestSources(psiClass)) {
      return false;
    }
    return true;
  }

  private boolean isUnderTestSources(PsiClass c) {
    ProjectRootManager rm = ProjectRootManager.getInstance(c.getProject());
    VirtualFile f = c.getContainingFile().getVirtualFile();
    if (f == null) return false;
    return rm.getFileIndex().isInTestSourceContent(f);
  }

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());

    final Module srcModule = ModuleUtil.findModuleForPsiElement(file);
    final PsiClass srcClass = getContainingClass(element);

    if (srcClass == null) return;

    PsiDirectory srcDir = file.getContainingDirectory();
    PsiPackage srcPackage = JavaDirectoryService.getInstance().getPackage(srcDir);

    final CreateTestDialog d = new CreateTestDialog(project,
                                                    getText(),
                                                    srcClass,
                                                    srcPackage,
                                                    srcModule);
    d.show();
    if (!d.isOK()) return;

    PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

              PsiClass targetClass = JavaDirectoryService.getInstance().createClass(d.getTargetDirectory(), d.getClassName());
              addSuperClass(targetClass, project, d.getSuperClassName());
              Editor editor = CodeInsightUtil.positionCursor(project, targetClass.getContainingFile(), targetClass.getLBrace());

              addTestMethods(editor,
                             targetClass,
                             d.getSelectedTestFrameworkDescriptor(),
                             d.getSelectedMethods(),
                             d.shouldGeneratedBefore(),
                             d.shouldGeneratedAfter());
            }
            catch (IncorrectOperationException e) {
              showErrorLater(project, d.getClassName());
            }
          }
        });
      }
    });
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

  private void addTestMethods(Editor editor,
                              PsiClass targetClass,
                              TestFrameworkDescriptor descriptor,
                              Collection<MemberInfo> methods,
                              boolean generateBefore,
                              boolean generateAfter) throws IncorrectOperationException {
    if (generateBefore) {
      generateMethod(TestIntegrationUtils.MethodKind.SET_UP, descriptor, targetClass, editor, "setUp");
    }
    if (generateAfter) {
      generateMethod(TestIntegrationUtils.MethodKind.TEAR_DOWN, descriptor, targetClass, editor, "tearUp");
    }
    for (MemberInfo m : methods) {
      generateMethod(TestIntegrationUtils.MethodKind.TEST, descriptor, targetClass, editor, m.getMember().getName());
    }
  }

  private void generateMethod(TestIntegrationUtils.MethodKind methodKind, TestFrameworkDescriptor descriptor, PsiClass targetClass, Editor editor, String name) {
    PsiMethod method = (PsiMethod)targetClass.add(TestIntegrationUtils.createDummyMethod(targetClass.getProject()));
    PsiDocumentManager.getInstance(targetClass.getProject()).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    TestIntegrationUtils.runTestMethodTemplate(methodKind, descriptor, editor, targetClass, method, name, true);
  }

  private PsiClass getContainingClass(PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
  }

  public boolean startInWriteAction() {
    return false;
  }
}