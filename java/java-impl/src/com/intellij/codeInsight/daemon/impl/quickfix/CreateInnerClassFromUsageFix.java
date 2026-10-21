// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeconstructionPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Creates an inner class for an unresolved reference. When there are several target classes, it asks the user to choose one.
 */
public final class CreateInnerClassFromUsageFix extends PsiBasedModCommandAction<PsiJavaCodeReferenceElement> {
  private final @NotNull CreateClassKind myKind;

  public CreateInnerClassFromUsageFix(@NotNull PsiJavaCodeReferenceElement refElement, @NotNull CreateClassKind kind) {
    super(refElement);
    myKind = kind;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.class.from.usage.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiJavaCodeReferenceElement element) {
    if (getPossibleTargets(element).isEmpty()) return null;
    String name = CreateClassFromUsageBaseFix.getAvailableClassName(myKind, element, context.offset());
    if (name == null) return null;
    return Presentation.of(QuickFixBundle.message("create.inner.class.from.usage.text", myKind.getDescriptionAccusative(), name));
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiJavaCodeReferenceElement element) {
    List<CreateInTargetClassAction> actions = ContainerUtil.map(
      getPossibleTargets(element), targetClass -> new CreateInTargetClassAction(element, targetClass));
    return ModCommand.chooseAction(QuickFixBundle.message("target.class.chooser.title"), actions);
  }

  private static @NotNull List<PsiClass> getPossibleTargets(@NotNull PsiJavaCodeReferenceElement element) {
    List<PsiClass> result = new ArrayList<>();
    PsiElement run = element;
    PsiMember contextMember = PsiTreeUtil.getParentOfType(run, PsiMember.class);

    while (contextMember != null) {
      if (contextMember instanceof PsiClass aClass && !(contextMember instanceof PsiTypeParameter)) {
        if (!isUsedInExtends(run, aClass)) {
          result.add(aClass);
        }
      }
      run = contextMember;
      contextMember = PsiTreeUtil.getParentOfType(run, PsiMember.class);
    }

    return result;
  }

  private static boolean isUsedInExtends(PsiElement element, PsiClass psiClass) {
    final PsiReferenceList extendsList = psiClass.getExtendsList();
    final PsiReferenceList implementsList = psiClass.getImplementsList();
    if (extendsList != null && PsiTreeUtil.isAncestor(extendsList, element, false)) {
      return true;
    }

    return implementsList != null && PsiTreeUtil.isAncestor(implementsList, element, false);
  }

  /**
   * Adds the new class into the target class. It adds the modifiers, the super class reference and the
   * type parameters. It starts no template.
   *
   * @param aClass         the class which gets the new class
   * @param ref            the reference which needs the new class
   * @param superClassName the qualified name of the super class, or null when the class needs none
   * @return the new class
   */
  private @NotNull PsiClass createInnerClass(@NotNull PsiClass aClass,
                                             @NotNull PsiJavaCodeReferenceElement ref,
                                             @Nullable String superClassName) {
    String refName = Objects.requireNonNull(ref.getReferenceName());
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(aClass.getProject());
    PsiClass created = myKind.create(elementFactory, refName);
    final PsiModifierList modifierList = Objects.requireNonNull(created.getModifierList());
    if (aClass.isInterface() || PsiUtil.isLocalOrAnonymousClass(aClass)) {
      modifierList.setModifierProperty(PsiModifier.PACKAGE_LOCAL, true);
    }
    else {
      modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
    }
    if (CommonJavaRefactoringUtil.isInStaticContext(ref, aClass) && !aClass.isInterface()) {
      modifierList.setModifierProperty(PsiModifier.STATIC, true);
    }
    if (superClassName != null) {
      CreateFromUsageUtils.setupSuperClassReference(created, superClassName);
    }
    CreateFromUsageUtils.setupGenericParameters(created, ref);
    return (PsiClass)aClass.add(created);
  }

  /**
   * Creates the class in one target class.
   */
  private final class CreateInTargetClassAction extends PsiUpdateModCommandAction<PsiJavaCodeReferenceElement> {
    private final @NotNull SmartPsiElementPointer<PsiClass> myTargetClass;

    private CreateInTargetClassAction(@NotNull PsiJavaCodeReferenceElement element, @NotNull PsiClass targetClass) {
      super(element);
      myTargetClass = SmartPointerManager.createPointer(targetClass);
    }

    @Override
    public @NotNull String getFamilyName() {
      return CreateInnerClassFromUsageFix.this.getFamilyName();
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiJavaCodeReferenceElement element) {
      PsiClass targetClass = myTargetClass.getElement();
      if (targetClass == null) return null;
      return Presentation.of(ClassPresentationUtil.getNameForClass(targetClass, false)).withIcon(targetClass.getIcon(0));
    }

    @Override
    protected void invoke(@NotNull ActionContext context,
                          @NotNull PsiJavaCodeReferenceElement element,
                          @NotNull ModPsiUpdater updater) {
      PsiClass targetClass = myTargetClass.getElement();
      if (targetClass == null) return;
      PsiDeconstructionPattern pattern = CreateClassFromUsageBaseFix.getDeconstructionPattern(element);
      PsiClass created = createInnerClass(updater.getWritable(targetClass), element,
                                          CreateClassFromUsageBaseFix.getSuperClassName(myKind, element));
      CreateFromUsageUtils.shortenClassReference(element, created);
      if (pattern == null) return;
      RecordingTemplateBuilder fields = new RecordingTemplateBuilder();
      CreateRecordFromNewFix.setupRecordComponentsFromPattern(created.getRecordHeader(), fields, pattern.getDeconstructionList());
      fields.startTemplate(updater, created, null);
    }
  }
}
