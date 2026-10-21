// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Creates an inner class or an inner record for the unresolved class of a {@code new} expression. When there are
 * several target classes, it asks the user to choose one.
 */
public final class CreateInnerClassFromNewFix extends PsiBasedModCommandAction<PsiNewExpression> {
  private final @NotNull CreateClassKind myKind;

  /**
   * @param newExpression the expression which creates an instance of the new class
   * @param kind          the kind of the new class: {@link CreateClassKind#CLASS} or {@link CreateClassKind#RECORD}
   */
  public CreateInnerClassFromNewFix(@NotNull PsiNewExpression newExpression, @NotNull CreateClassKind kind) {
    super(newExpression);
    myKind = kind;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.class.from.new.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiNewExpression newExpression) {
    if (!isInProject(newExpression)) return null;
    PsiJavaCodeReferenceElement ref = newExpression.getClassOrAnonymousClassReference();
    if (ref == null || ref.resolve() != null) return null;
    PsiElement nameElement = ref.getReferenceNameElement();
    if (!(nameElement instanceof PsiIdentifier)) return null;
    PsiFile targetFile = CreateClassFromNewFix.getTargetFile(newExpression);
    if (targetFile != null && !isInProject(targetFile)) return null;
    if (!CreateFromUsageUtils.shouldShowTag(context.offset(), nameElement, newExpression)) return null;
    if (getTargetClasses(newExpression, context.project()).isEmpty()) return null;
    return Presentation.of(QuickFixBundle.message("create.inner.class.from.usage.text", myKind.getDescriptionAccusative(),
                                                  nameElement.getText()));
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiNewExpression newExpression) {
    List<CreateInTargetClassAction> actions = ContainerUtil.map(
      getTargetClasses(newExpression, context.project()), targetClass -> new CreateInTargetClassAction(newExpression, targetClass));
    return ModCommand.chooseAction(QuickFixBundle.message("target.class.chooser.title"), actions);
  }

  private static boolean isInProject(@NotNull PsiElement element) {
    return element.getManager().isInProject(element) || ScratchUtil.isScratch(PsiUtilCore.getVirtualFile(element));
  }

  private @NotNull List<PsiClass> getTargetClasses(@NotNull PsiNewExpression newExpression, @NotNull Project project) {
    List<PsiClass> classes = CreateFromUsageUtils.filterTargetClasses(
      CreateFromUsageUtils.getTargetClasses(newExpression, true, psiClass -> false), project);
    if (myKind != CreateClassKind.RECORD) return classes;
    return ContainerUtil.filter(classes, cls -> cls.getContainingClass() == null || cls.hasModifierProperty(PsiModifier.STATIC));
  }

  /**
   * Adds the new class into the target class. It adds the modifiers and the type parameters. It adds no
   * constructor, and it starts no template.
   *
   * @param targetClass   the class which gets the new class
   * @param newExpression the expression which creates an instance of the new class
   * @return the new class, or null when the new expression has no class reference
   */
  private @Nullable PsiClass createInnerClass(@NotNull PsiClass targetClass, @NotNull PsiNewExpression newExpression) {
    PsiJavaCodeReferenceElement ref = newExpression.getClassOrAnonymousClassReference();
    if (ref == null) return null;
    String refName = Objects.requireNonNull(ref.getReferenceName());
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(newExpression.getProject());
    PsiClass created = myKind.create(elementFactory, refName);
    created = (PsiClass)targetClass.add(created);

    final PsiModifierList modifierList = Objects.requireNonNull(created.getModifierList());
    if (PsiTreeUtil.isAncestor(targetClass, newExpression, true)) {
      if (targetClass.isInterface() || PsiUtil.isLocalOrAnonymousClass(targetClass)) {
        modifierList.setModifierProperty(PsiModifier.PACKAGE_LOCAL, true);
      } else {
        modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
      }
    }

    if (!created.hasModifierProperty(PsiModifier.STATIC) &&
        newExpression.getQualifier() == null &&
        (!PsiTreeUtil.isAncestor(targetClass, newExpression, true) || PsiUtil.getEnclosingStaticElement(newExpression, targetClass) != null || isInThisOrSuperCall(newExpression))) {
      modifierList.setModifierProperty(PsiModifier.STATIC, true);
    }

    CreateFromUsageUtils.setupGenericParameters(created, ref);
    return created;
  }

  /**
   * Adds the super types and the constructor or the record components, which the new expression needs.
   * It starts no template.
   *
   * @param created       the new class
   * @param newExpression the expression which creates an instance of the new class
   * @param builder       the builder which gets one field per parameter
   * @return the element after which the template puts the caret, or null when the template chooses the
   * position itself, or when the class needs no constructor
   */
  private @Nullable PsiElement setupNewClass(@NotNull PsiClass created,
                                             @NotNull PsiNewExpression newExpression,
                                             @NotNull RecordingTemplateBuilder builder) {
    CreateClassFromNewFix.setupInheritance(newExpression, created);
    PsiExpressionList argList = newExpression.getArgumentList();
    if (argList == null || argList.isEmpty()) return null;
    if (myKind == CreateClassKind.RECORD) {
      CreateRecordFromNewFix.setupRecordComponents(created.getRecordHeader(), builder, argList,
                                                   CreateFromUsageUtils.getTargetSubstitutor(newExpression));
      return null;
    }
    return CreateClassFromNewFix.createConstructor(created, newExpression, argList, builder);
  }

  /**
   * Creates the class in one target class.
   */
  private final class CreateInTargetClassAction extends PsiUpdateModCommandAction<PsiNewExpression> {
    private final @NotNull SmartPsiElementPointer<PsiClass> myTargetClass;

    private CreateInTargetClassAction(@NotNull PsiNewExpression newExpression, @NotNull PsiClass targetClass) {
      super(newExpression);
      myTargetClass = SmartPointerManager.createPointer(targetClass);
    }

    @Override
    public @NotNull String getFamilyName() {
      return CreateInnerClassFromNewFix.this.getFamilyName();
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiNewExpression newExpression) {
      PsiClass targetClass = myTargetClass.getElement();
      if (targetClass == null) return null;
      return Presentation.of(ClassPresentationUtil.getNameForClass(targetClass, false)).withIcon(targetClass.getIcon(0));
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiNewExpression newExpression, @NotNull ModPsiUpdater updater) {
      PsiClass targetClass = myTargetClass.getElement();
      if (targetClass == null) return;
      PsiClass created = createInnerClass(updater.getWritable(targetClass), newExpression);
      if (created == null) return;
      PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
      if (classReference != null) {
        CreateFromUsageUtils.shortenClassReference(classReference, created);
      }
      RecordingTemplateBuilder fields = new RecordingTemplateBuilder();
      PsiElement endAfter = setupNewClass(created, newExpression, fields);
      fields.startTemplate(updater, created, endAfter);
    }
  }

  private static boolean isInThisOrSuperCall(PsiNewExpression newExpression) {
    final PsiExpressionStatement expressionStatement = PsiTreeUtil.getParentOfType(newExpression, PsiExpressionStatement.class);
    if (expressionStatement != null) {
      final PsiExpression expression = expressionStatement.getExpression();
      if (JavaPsiConstructorUtil.isConstructorCall(expression)) {
        return true;
      }
    }
    return false;
  }
}
