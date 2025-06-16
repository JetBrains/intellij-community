// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.impl.RunRefactoringAction;
import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.refactoring.memberPullUp.JavaPullUpHandlerBase;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class PullAsAbstractUpFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction {
  private static final Logger LOG = Logger.getInstance(PullAsAbstractUpFix.class);
  private final @IntentionName String myName;

  public PullAsAbstractUpFix(PsiMethod psiMethod, final @IntentionName String name) {
    super(psiMethod);
    myName = name;
  }

  @Override
  public @NotNull String getText() {
    return myName;
  }

  @Override
  public @NotNull String getFamilyName() {
    return CommonBundle.message("title.pull.up");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile psiFile,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return startElement instanceof PsiMethod && ((PsiMethod)startElement).getContainingClass() != null;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile psiFile,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiMethod method = (PsiMethod)startElement;
    if (!FileModificationService.getInstance().prepareFileForWrite(method.getContainingFile())) return;

    final PsiClass containingClass = method.getContainingClass();
    LOG.assertTrue(containingClass != null);

    if (containingClass instanceof PsiAnonymousClass) {
      final PsiClassType baseClassType = ((PsiAnonymousClass)containingClass).getBaseClassType();
      final PsiClass baseClass = baseClassType.resolve();
      if (baseClass != null && BaseIntentionAction.canModify(baseClass)) {
        pullUp(method, containingClass, baseClass);
      }
    }
    else {
      AtomicBoolean noClassesFound = new AtomicBoolean(false);
      PsiTargetNavigator<PsiClass> navigator = new PsiTargetNavigator<>(() -> {
        final LinkedHashSet<PsiClass> classesToPullUp = new LinkedHashSet<>();
        collectClassesToPullUp(classesToPullUp, containingClass.getExtendsListTypes());
        collectClassesToPullUp(classesToPullUp, containingClass.getImplementsListTypes());
        noClassesFound.set(classesToPullUp.isEmpty());
        return new ArrayList<>(classesToPullUp);
      });
      PsiElementProcessor<PsiClass> processor = element -> {
        pullUp(method, containingClass, element);
        return false;
      };
      if (editor != null) {
        navigator.navigate(editor, JavaBundle.message("choose.super.class.popup.title"), processor);
      }
      else {
        navigator.performSilently(processor);
      }

      if (noClassesFound.get()) {
        //check visibility
        var supportProvider = LanguageRefactoringSupport.getInstance().forLanguage(JavaLanguage.INSTANCE);
        var handler = supportProvider.getExtractInterfaceHandler();
        if (handler == null)  {
          throw new IllegalStateException("Handler is null, supportProvider class = " + supportProvider.getClass());
        }
        handler.invoke(project, new PsiElement[]{containingClass}, null);
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
    var supportProvider = LanguageRefactoringSupport.getInstance().forLanguage(JavaLanguage.INSTANCE);
    var handler = (JavaPullUpHandlerBase)supportProvider.getPullUpHandler();
    if (handler == null)  {
      throw new IllegalStateException("Handler is null, supportProvider class = " + supportProvider.getClass());
    }
    handler.runSilently(containingClass, baseClass, new MemberInfo[]{memberInfo}, new DocCommentPolicy(DocCommentPolicy.ASIS));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static void registerQuickFix(@NotNull PsiMethod methodWithOverrides, @NotNull List<? super IntentionAction> registrar) {
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

    registrar.add(new PullAsAbstractUpFix(methodWithOverrides, name));
    if (canBePulledUp) {
      var supportProvider = LanguageRefactoringSupport.getInstance().forLanguage(JavaLanguage.INSTANCE);
      registrar.add(new RunRefactoringAction(supportProvider.getPullUpHandler(), JavaBundle.message("pull.members.up.fix.name")));
    }


    if (! (containingClass instanceof PsiAnonymousClass)){
      var supportProvider = LanguageRefactoringSupport.getInstance().forLanguage(JavaLanguage.INSTANCE);

      registrar.add(new RunRefactoringAction(supportProvider.getExtractInterfaceHandler(), JavaBundle.message("extract.interface.command.name")));
      registrar.add(new RunRefactoringAction(supportProvider.getExtractSuperClassHandler(), JavaBundle.message("extract.superclass.command.name")));
    }
  }
}
