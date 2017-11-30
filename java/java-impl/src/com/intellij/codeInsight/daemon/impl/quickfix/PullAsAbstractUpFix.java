/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.intention.impl.RunRefactoringAction;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.refactoring.extractInterface.ExtractInterfaceHandler;
import com.intellij.refactoring.extractSuperclass.ExtractSuperclassHandler;
import com.intellij.refactoring.memberPullUp.JavaPullUpHandler;
import com.intellij.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;

public class PullAsAbstractUpFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance(PullAsAbstractUpFix.class);
  private final String myName;

  public PullAsAbstractUpFix(PsiMethod psiMethod, final String name) {
    super(psiMethod);
    myName = name;
  }

  @Override
  @NotNull
  public String getText() {
    return myName;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return "Pull up";
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return startElement instanceof PsiMethod && ((PsiMethod)startElement).getContainingClass() != null;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable("is null when called from inspection") Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiMethod method = (PsiMethod)startElement;
    if (!FileModificationService.getInstance().prepareFileForWrite(method.getContainingFile())) return;

    final PsiClass containingClass = method.getContainingClass();
    LOG.assertTrue(containingClass != null);

    PsiManager manager = containingClass.getManager();
    if (containingClass instanceof PsiAnonymousClass) {
      final PsiClassType baseClassType = ((PsiAnonymousClass)containingClass).getBaseClassType();
      final PsiClass baseClass = baseClassType.resolve();
      if (baseClass != null && manager.isInProject(baseClass)) {
        pullUp(method, containingClass, baseClass);
      }
    }
    else {
      final LinkedHashSet<PsiClass> classesToPullUp = new LinkedHashSet<>();
      collectClassesToPullUp(manager, classesToPullUp, containingClass.getExtendsListTypes());
      collectClassesToPullUp(manager, classesToPullUp, containingClass.getImplementsListTypes());

      if (classesToPullUp.isEmpty()) {
        //check visibility
        new ExtractInterfaceHandler().invoke(project, new PsiElement[]{containingClass}, null);
      }
      else if (classesToPullUp.size() == 1) {
        pullUp(method, containingClass, classesToPullUp.iterator().next());
      }
      else if (editor != null) {
        NavigationUtil.getPsiElementPopup(classesToPullUp.toArray(new PsiClass[classesToPullUp.size()]), new PsiClassListCellRenderer(),
                                          "Choose super class",
                                          new PsiElementProcessor<PsiClass>() {
                                            @Override
                                            public boolean execute(@NotNull PsiClass aClass) {
                                              pullUp(method, containingClass, aClass);
                                              return false;
                                            }
                                          }, classesToPullUp.iterator().next()).showInBestPositionFor(editor);
      }
    }
  }


  private static void collectClassesToPullUp(PsiManager manager, LinkedHashSet<PsiClass> classesToPullUp, PsiClassType[] extendsListTypes) {
    for (PsiClassType extendsListType : extendsListTypes) {
      PsiClass resolve = extendsListType.resolve();
      if (resolve != null && manager.isInProject(resolve)) {
        classesToPullUp.add(resolve);
      }
    }
  }

  private static void pullUp(PsiMethod method, PsiClass containingClass, PsiClass baseClass) {
    if (!FileModificationService.getInstance().prepareFileForWrite(baseClass.getContainingFile())) return;
    final MemberInfo memberInfo = new MemberInfo(method);
    memberInfo.setChecked(true);
    memberInfo.setToAbstract(true);
    new PullUpProcessor(containingClass, baseClass, new MemberInfo[]{memberInfo}, new DocCommentPolicy(DocCommentPolicy.ASIS)).run();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static void registerQuickFix(@NotNull PsiMethod methodWithOverrides, @NotNull QuickFixActionRegistrar registrar) {
    PsiClass containingClass = methodWithOverrides.getContainingClass();
    if (containingClass == null) return;
    final PsiManager manager = containingClass.getManager();

    boolean canBePulledUp = true;
    String name = "Pull method \'" + methodWithOverrides.getName() + "\' up";
    if (containingClass instanceof PsiAnonymousClass) {
      final PsiClassType baseClassType = ((PsiAnonymousClass)containingClass).getBaseClassType();
      final PsiClass baseClass = baseClassType.resolve();
      if (baseClass == null) return;
      if (!manager.isInProject(baseClass)) return;
      if (!baseClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        name = "Pull method \'" + methodWithOverrides.getName() + "\' up and make it abstract";
      }
    } else {
      final LinkedHashSet<PsiClass> classesToPullUp = new LinkedHashSet<>();
      collectClassesToPullUp(manager, classesToPullUp, containingClass.getExtendsListTypes());
      collectClassesToPullUp(manager, classesToPullUp, containingClass.getImplementsListTypes());
      if (classesToPullUp.isEmpty()) {
        name = "Extract method \'" + methodWithOverrides.getName() + "\' to new interface";
        canBePulledUp = false;
      } else if (classesToPullUp.size() == 1) {
        final PsiClass baseClass = classesToPullUp.iterator().next();
        name = "Pull method \'" + methodWithOverrides.getName() + "\' to \'" + baseClass.getName() + "\'";
        if (!baseClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
           name+= " and make it abstract";
        }
      }
      registrar.register(new RunRefactoringAction(new ExtractInterfaceHandler(), "Extract interface"));
      registrar.register(new RunRefactoringAction(new ExtractSuperclassHandler(), "Extract superclass"));
    }


    if (canBePulledUp) {
      registrar.register(new RunRefactoringAction(new JavaPullUpHandler(), "Pull members up"));
    }
    registrar.register(new PullAsAbstractUpFix(methodWithOverrides, name));
  }
}
