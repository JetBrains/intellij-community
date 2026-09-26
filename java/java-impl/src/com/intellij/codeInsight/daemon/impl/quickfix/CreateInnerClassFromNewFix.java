// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class CreateInnerClassFromNewFix extends CreateClassFromNewFix {
  private static final Logger LOG = Logger.getInstance(CreateInnerClassFromNewFix.class);

  public CreateInnerClassFromNewFix(final PsiNewExpression expr) {
    super(expr);
  }

  @Override
  public String getText(String varName) {
    return QuickFixBundle.message("create.inner.class.from.usage.text", getKind().getDescriptionAccusative(), varName);
  }

  @Override
  protected boolean isAllowOuterTargetClass() {
    return true;
  }

  @Override
  protected boolean isValidElement(PsiElement element) {
    PsiJavaCodeReferenceElement ref = element instanceof PsiNewExpression ? ((PsiNewExpression)element).getClassOrAnonymousClassReference() : null;
    return ref != null && ref.resolve() != null;
  }

  @Override
  protected boolean rejectContainer(PsiNewExpression qualifier) {
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    chooseTargetClass(project, editor, this::invokeImpl);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiElement element = PsiTreeUtil.findSameElementInCopy(getElement(), psiFile);
    List<PsiClass> targetClasses = filterTargetClasses(element, project);
    if (targetClasses.isEmpty()) return IntentionPreviewInfo.EMPTY;
    PsiClass targetClass = targetClasses.getFirst();
    // the target class may be resolved through the qualifier of the new expression and thus belong to another,
    // physical file; such a change cannot be rendered in the custom copy-based preview
    if (targetClass.getContainingFile() != psiFile) return IntentionPreviewInfo.EMPTY;
    invokeImpl(targetClass);
    return IntentionPreviewInfo.DIFF;
  }

  private void invokeImpl(final PsiClass targetClass) {
    PsiNewExpression newExpression = getNewExpression();
    if (!targetClass.isPhysical()) {
      newExpression = PsiTreeUtil.findSameElementInCopy(newExpression, targetClass.getContainingFile());
    }
    PsiClass created = createInnerClass(targetClass, newExpression);
    if (created == null) return;
    setupClassFromNewExpression(created, newExpression);
  }

  /**
   * Adds the new class into the target class. It adds the modifiers and the type parameters. It adds no
   * constructor, and it starts no template, so a {@link com.intellij.modcommand.ModCommandAction} can
   * call it.
   *
   * @param targetClass   the class which gets the new class
   * @param newExpression the expression which creates an instance of the new class
   * @return the new class, or null when the new expression has no class reference
   */
  protected @Nullable PsiClass createInnerClass(@NotNull PsiClass targetClass, @NotNull PsiNewExpression newExpression) {
    PsiJavaCodeReferenceElement ref = newExpression.getClassOrAnonymousClassReference();
    if (ref == null) return null;
    String refName = ref.getReferenceName();
    LOG.assertTrue(refName != null);
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(newExpression.getProject());
    PsiClass created = getKind().create(elementFactory, refName);
    created = (PsiClass)targetClass.add(created);

    final PsiModifierList modifierList = created.getModifierList();
    LOG.assertTrue(modifierList != null);
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

    setupGenericParameters(created, ref);
    return created;
  }

  @Override
  public @Nullable ModCommandAction getFallbackModCommandAction() {
    PsiNewExpression newExpression = getNewExpression();
    return newExpression == null ? null : new CreateInnerClassFromNewModCommandAction(newExpression);
  }

  /**
   * Creates the class in the first target class. The fix which the user starts in the editor asks for the
   * target class, which a {@link ModCommandAction} cannot do.
   */
  private final class CreateInnerClassFromNewModCommandAction extends PsiUpdateModCommandAction<PsiNewExpression> {
    private CreateInnerClassFromNewModCommandAction(@NotNull PsiNewExpression newExpression) {
      super(newExpression);
    }

    @Override
    public @NotNull String getFamilyName() {
      return CreateInnerClassFromNewFix.this.getFamilyName();
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiNewExpression newExpression) {
      String text = getAvailableText(context.project(), context.offset());
      return text == null ? null : Presentation.of(text);
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiNewExpression newExpression, @NotNull ModPsiUpdater updater) {
      List<PsiClass> targetClasses = filterTargetClasses(newExpression, context.project());
      if (targetClasses.isEmpty()) return;
      PsiClass created = createInnerClass(updater.getWritable(targetClasses.getFirst()), newExpression);
      if (created == null) return;
      setupNewClass(created, newExpression, DummyTemplateBuilder.INSTANCE);
      updater.moveCaretTo(ObjectUtils.notNull(created.getNameIdentifier(), created));
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