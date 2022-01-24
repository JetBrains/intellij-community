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

import com.intellij.CommonBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.impl.RunRefactoringAction;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.java.JavaBundle;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;

public class PullAsAbstractUpFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction {
  private static final Logger LOG = Logger.getInstance(PullAsAbstractUpFix.class);
  private final @IntentionName String myName;

  public PullAsAbstractUpFix(PsiMethod psiMethod, final @IntentionName String name) {
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
    return CommonBundle.message("title.pull.up");
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
                     @Nullable Editor editor,
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
      if (baseClass != null && BaseIntentionAction.canModify(baseClass)) {
        pullUp(method, containingClass, baseClass);
      }
    }
    else {
      final LinkedHashSet<PsiClass> classesToPullUp = new LinkedHashSet<>();
      collectClassesToPullUp(classesToPullUp, containingClass.getExtendsListTypes());
      collectClassesToPullUp(classesToPullUp, containingClass.getImplementsListTypes());

      if (classesToPullUp.isEmpty()) {
        //check visibility
        var supportProvider = LanguageRefactoringSupport.INSTANCE.forLanguage(JavaLanguage.INSTANCE);
        var handler = supportProvider.getExtractInterfaceHandler();
        assert handler != null;
        handler.invoke(project, new PsiElement[]{containingClass}, null);
      }
      else if (classesToPullUp.size() == 1) {
        pullUp(method, containingClass, classesToPullUp.iterator().next());
      }
      else if (editor != null) {
        NavigationUtil.getPsiElementPopup(classesToPullUp.toArray(PsiClass.EMPTY_ARRAY), new PsiClassListCellRenderer(),
                                          JavaBundle.message("choose.super.class.popup.title"),
                                          new PsiElementProcessor<>() {
                                            @Override
                                            public boolean execute(@NotNull PsiClass aClass) {
                                              pullUp(method, containingClass, aClass);
                                              return false;
                                            }
                                          }, classesToPullUp.iterator().next()).showInBestPositionFor(editor);
      }
    }
  }


  private static void collectClassesToPullUp(LinkedHashSet<? super PsiClass> classesToPullUp, PsiClassType[] extendsListTypes) {
    for (PsiClassType extendsListType : extendsListTypes) {
      PsiClass resolve = extendsListType.resolve();
      if (resolve != null && BaseIntentionAction.canModify(resolve)) {
        classesToPullUp.add(resolve);
      }
    }
  }

  private static void pullUp(PsiMethod method, PsiClass containingClass, PsiClass baseClass) {
    if (!FileModificationService.getInstance().prepareFileForWrite(baseClass.getContainingFile())) return;
    final MemberInfo memberInfo = new MemberInfo(method);
    memberInfo.setChecked(true);
    memberInfo.setToAbstract(true);
    var provider = JavaSpecialRefactoringProvider.getInstance();
    provider.runPullUpProcessor(containingClass, baseClass, new MemberInfo[]{memberInfo}, new DocCommentPolicy<>(DocCommentPolicy.ASIS));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static void registerQuickFix(@NotNull PsiMethod methodWithOverrides, @NotNull QuickFixActionRegistrar registrar) {
    PsiClass containingClass = methodWithOverrides.getContainingClass();
    if (containingClass == null) return;

    boolean canBePulledUp = true;
    String name = JavaBundle.message("intention.name.pull.method.up", methodWithOverrides.getName());
    if (containingClass instanceof PsiAnonymousClass) {
      final PsiClassType baseClassType = ((PsiAnonymousClass)containingClass).getBaseClassType();
      final PsiClass baseClass = baseClassType.resolve();
      if (baseClass == null) return;
      if (!BaseIntentionAction.canModify(baseClass)) return;
      if (!baseClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        name = JavaBundle.message("intention.name.pull.method.up.make.it.abstract", methodWithOverrides.getName());
      }
    } else {
      final LinkedHashSet<PsiClass> classesToPullUp = new LinkedHashSet<>();
      collectClassesToPullUp(classesToPullUp, containingClass.getExtendsListTypes());
      collectClassesToPullUp(classesToPullUp, containingClass.getImplementsListTypes());
      if (classesToPullUp.isEmpty()) {
        name = JavaBundle.message("intention.name.extract.method.to.new.interface", methodWithOverrides.getName());
        canBePulledUp = false;
      } else if (classesToPullUp.size() == 1) {
        final PsiClass baseClass = classesToPullUp.iterator().next();
        name = JavaBundle.message("intention.name.pull.method.up.and.make.it.abstract.conditionally", methodWithOverrides.getName(), baseClass.getName(), !baseClass.hasModifierProperty(PsiModifier.ABSTRACT) ? 0 : 1);
      }
    }

    registrar.register(new PullAsAbstractUpFix(methodWithOverrides, name));
    if (canBePulledUp) {
      var supportProvider = LanguageRefactoringSupport.INSTANCE.forLanguage(JavaLanguage.INSTANCE);
      registrar.register(new RunRefactoringAction(supportProvider.getPullUpHandler(), JavaBundle.message("pull.members.up.fix.name")));
    }


    if (! (containingClass instanceof PsiAnonymousClass)){
      var supportProvider = LanguageRefactoringSupport.INSTANCE.forLanguage(JavaLanguage.INSTANCE);

      registrar.register(new RunRefactoringAction(supportProvider.getExtractInterfaceHandler(), JavaBundle.message("extract.interface.command.name")));
      registrar.register(new RunRefactoringAction(supportProvider.getExtractSuperClassHandler(), JavaBundle.message("extract.superclass.command.name")));
    }
  }
}
