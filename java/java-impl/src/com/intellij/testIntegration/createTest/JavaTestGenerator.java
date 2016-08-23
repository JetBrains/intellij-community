/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.template.Template;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testIntegration.TestFramework;
import com.intellij.testIntegration.TestIntegrationUtils;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class JavaTestGenerator implements TestGenerator {
  public JavaTestGenerator() {
  }

  public PsiElement generateTest(final Project project, final CreateTestDialog d) {
    return PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(
      () -> ApplicationManager.getApplication().runWriteAction(new Computable<PsiElement>() {
        public PsiElement compute() {
          try {
            IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

            PsiClass targetClass = createTestClass(d);
            if (targetClass == null) {
              return null;
            }
            final TestFramework frameworkDescriptor = d.getSelectedTestFrameworkDescriptor();
            final String defaultSuperClass = frameworkDescriptor.getDefaultSuperClass();
            final String superClassName = d.getSuperClassName();
            if (!Comparing.strEqual(superClassName, defaultSuperClass)) {
              addSuperClass(targetClass, project, superClassName);
            }

            Editor editor = CodeInsightUtil.positionCursorAtLBrace(project, targetClass.getContainingFile(), targetClass);
            addTestMethods(editor,
                           targetClass,
                           d.getTargetClass(),
                           frameworkDescriptor,
                           d.getSelectedMethods(),
                           d.shouldGeneratedBefore(),
                           d.shouldGeneratedAfter());
            return targetClass;
          }
          catch (IncorrectOperationException e) {
            showErrorLater(project, d.getClassName());
            return null;
          }
        }
      }));
  }

  @Nullable
  private static PsiClass createTestClass(CreateTestDialog d) {
    final TestFramework testFrameworkDescriptor = d.getSelectedTestFrameworkDescriptor();
    final FileTemplateDescriptor fileTemplateDescriptor = TestIntegrationUtils.MethodKind.TEST_CLASS.getFileTemplateDescriptor(testFrameworkDescriptor);
    final PsiDirectory targetDirectory = d.getTargetDirectory();

    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(targetDirectory);
    if (aPackage != null) {
      final GlobalSearchScope scope = GlobalSearchScopesCore.directoryScope(targetDirectory, false);
      final PsiClass[] classes = aPackage.findClassByShortName(d.getClassName(), scope);
      if (classes.length > 0) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(classes[0])) {
          return null;
        }
        return classes[0];
      }
    }

    if (fileTemplateDescriptor != null) {
      final PsiClass classFromTemplate = createTestClassFromCodeTemplate(d, fileTemplateDescriptor, targetDirectory);
      if (classFromTemplate != null) {
        return classFromTemplate;
      }
    }

    return JavaDirectoryService.getInstance().createClass(targetDirectory, d.getClassName());
  }

  private static PsiClass createTestClassFromCodeTemplate(final CreateTestDialog d,
                                                          final FileTemplateDescriptor fileTemplateDescriptor,
                                                          final PsiDirectory targetDirectory) {
    final String templateName = fileTemplateDescriptor.getFileName();
    final FileTemplate fileTemplate = FileTemplateManager.getInstance(targetDirectory.getProject()).getCodeTemplate(templateName);
    final Properties defaultProperties = FileTemplateManager.getInstance(targetDirectory.getProject()).getDefaultProperties();
    Properties properties = new Properties(defaultProperties);
    properties.setProperty(FileTemplate.ATTRIBUTE_NAME, d.getClassName());
    final PsiClass targetClass = d.getTargetClass();
    if (targetClass != null && targetClass.isValid()) {
      properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, targetClass.getQualifiedName());
    }
    try {
      final PsiElement psiElement = FileTemplateUtil.createFromTemplate(fileTemplate, templateName, properties, targetDirectory);
      if (psiElement instanceof PsiClass) {
        return (PsiClass)psiElement;
      }
      return null;
    }
    catch (Exception e) {
      return null;
    }
  }

  private static void addSuperClass(PsiClass targetClass, Project project, String superClassName) throws IncorrectOperationException {
    if (superClassName == null) return;
    final PsiReferenceList extendsList = targetClass.getExtendsList();
    if (extendsList == null) return;

    PsiElementFactory ef = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiJavaCodeReferenceElement superClassRef;

    PsiClass superClass = findClass(project, superClassName);
    if (superClass != null) {
      superClassRef = ef.createClassReferenceElement(superClass);
    }
    else {
      superClassRef = ef.createFQClassNameReferenceElement(superClassName, GlobalSearchScope.allScope(project));
    }
    final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
    if (referenceElements.length == 0) {
      extendsList.add(superClassRef);
    } else {
      referenceElements[0].replace(superClassRef);
    }
  }

  @Nullable
  private static PsiClass findClass(Project project, String fqName) {
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    return JavaPsiFacade.getInstance(project).findClass(fqName, scope);
  }

  public static void addTestMethods(Editor editor,
                                    PsiClass targetClass,
                                    final TestFramework descriptor,
                                    Collection<MemberInfo> methods,
                                    boolean generateBefore,
                                    boolean generateAfter) throws IncorrectOperationException {
    addTestMethods(editor, targetClass, null, descriptor, methods, generateBefore, generateAfter);
  }

  public static void addTestMethods(Editor editor,
                                    PsiClass targetClass,
                                    @Nullable PsiClass sourceClass, 
                                    final TestFramework descriptor,
                                    Collection<MemberInfo> methods,
                                    boolean generateBefore,
                                    boolean generateAfter) throws IncorrectOperationException {
    final Set<String> existingNames = new HashSet<>();
    PsiMethod anchor = null;
    if (generateBefore && descriptor.findSetUpMethod(targetClass) == null) {
      anchor = generateMethod(TestIntegrationUtils.MethodKind.SET_UP, descriptor, targetClass, sourceClass, editor, null, existingNames, null);
    }

    if (generateAfter && descriptor.findTearDownMethod(targetClass) == null) {
      anchor = generateMethod(TestIntegrationUtils.MethodKind.TEAR_DOWN, descriptor, targetClass, sourceClass, editor, null, existingNames, anchor);
    }

    final Template template = TestIntegrationUtils.createTestMethodTemplate(TestIntegrationUtils.MethodKind.TEST, descriptor,
                                                                            targetClass, sourceClass, null, true, existingNames);
    final String prefix = JavaPsiFacade.getElementFactory(targetClass.getProject()).createMethodFromText(template.getTemplateText(), targetClass).getName();
    existingNames.addAll(ContainerUtil.map(targetClass.getMethods(), method -> StringUtil.decapitalize(StringUtil.trimStart(method.getName(), prefix))));

    for (MemberInfo m : methods) {
      anchor = generateMethod(TestIntegrationUtils.MethodKind.TEST, descriptor, targetClass, sourceClass, editor, m.getMember().getName(), existingNames, anchor);
    }
  }

  private static void showErrorLater(final Project project, final String targetClassName) {
    ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(project,
                                                                               CodeInsightBundle.message("intention.error.cannot.create.class.message", targetClassName),
                                                                               CodeInsightBundle.message("intention.error.cannot.create.class.title")));
  }

  private static PsiMethod generateMethod(@NotNull TestIntegrationUtils.MethodKind methodKind,
                                          TestFramework descriptor,
                                          PsiClass targetClass,
                                          @Nullable PsiClass sourceClass,
                                          Editor editor,
                                          @Nullable String name,
                                          Set<String> existingNames, PsiMethod anchor) {
    PsiMethod method = (PsiMethod)targetClass.addAfter(TestIntegrationUtils.createDummyMethod(targetClass), anchor);
    PsiDocumentManager.getInstance(targetClass.getProject()).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    TestIntegrationUtils.runTestMethodTemplate(methodKind, descriptor, editor, targetClass, sourceClass, method, name, true, existingNames);
    return method;
  }

  @Override
  public String toString() {
    return CodeInsightBundle.message("intention.create.test.dialog.java");
  }
}