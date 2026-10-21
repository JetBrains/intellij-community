// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.ModTemplateBuilder;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JVMElementFactories;
import com.intellij.psi.JVMElementFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Creates a field for a reference which resolves to a field that the place of the reference cannot use.
 * When there are several target classes, it asks the user to choose one.
 */
public final class CreateFieldFromUsageFix extends PsiBasedModCommandAction<PsiReferenceExpression> {
  public CreateFieldFromUsageFix(@NotNull PsiReferenceExpression referenceElement) {
    super(referenceElement);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.field.from.usage.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiReferenceExpression ref) {
    if (!BaseIntentionAction.canModify(ref) || ref.getParent() instanceof PsiMethodCallExpression) return null;
    if (ref.getReferenceNameElement() == null || CreateFromUsageUtils.isValidReference(ref, false)) return null;
    if (getTargetClasses(ref, context.project()).isEmpty()) return null;
    return Presentation.of(CommonQuickFixBundle.message("fix.create.title.x", JavaElementKind.FIELD.object(), ref.getReferenceName()));
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiReferenceExpression ref) {
    List<CreateInTargetClassAction> actions = ContainerUtil.map(
      getTargetClasses(ref, context.project()), targetClass -> new CreateInTargetClassAction(ref, targetClass));
    return ModCommand.chooseAction(QuickFixBundle.message("target.class.chooser.title"), actions);
  }

  private static @NotNull List<PsiClass> getTargetClasses(@NotNull PsiReferenceExpression ref, @NotNull Project project) {
    PsiElement target = ref.resolve();
    List<PsiClass> classes = ContainerUtil.filter(
      CreateFromUsageUtils.getTargetClasses(ref, true, psiClass -> BaseIntentionAction.canModify(psiClass) && canHaveInstanceField(psiClass)),
      psiClass -> BaseIntentionAction.canModify(psiClass) &&
                  (canHaveInstanceField(psiClass) || CreateFromUsageUtils.shouldCreateStaticMember(ref, psiClass)) &&
                  !(target instanceof PsiField field && field.getContainingClass() == psiClass));
    return CreateFromUsageUtils.filterTargetClasses(classes, project);
  }

  private static boolean canHaveInstanceField(@NotNull PsiClass psiClass) {
    return !psiClass.isInterface() && !psiClass.isAnnotationType() && !psiClass.isRecord();
  }

  /**
   * Creates the field in one target class.
   */
  private final class CreateInTargetClassAction extends PsiUpdateModCommandAction<PsiReferenceExpression> {
    private final @NotNull SmartPsiElementPointer<PsiClass> myTargetClass;

    private CreateInTargetClassAction(@NotNull PsiReferenceExpression ref, @NotNull PsiClass targetClass) {
      super(ref);
      myTargetClass = SmartPointerManager.createPointer(targetClass);
    }

    @Override
    public @NotNull String getFamilyName() {
      return CreateFieldFromUsageFix.this.getFamilyName();
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiReferenceExpression ref) {
      PsiClass targetClass = myTargetClass.getElement();
      if (targetClass == null) return null;
      return Presentation.of(ClassPresentationUtil.getNameForClass(targetClass, false)).withIcon(targetClass.getIcon(0));
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiReferenceExpression ref, @NotNull ModPsiUpdater updater) {
      PsiClass originalTarget = myTargetClass.getElement();
      if (originalTarget == null) return;
      PsiClass targetClass = updater.getWritable(originalTarget);
      String fieldName = ref.getReferenceName();
      if (fieldName == null) return;
      Project project = context.project();
      JVMElementFactory factory = JVMElementFactories.getFactory(targetClass.getLanguage(), project);
      if (factory == null) factory = JavaPsiFacade.getElementFactory(project);

      PsiMember enclosingContext = null;
      PsiClass parentClass;
      do {
        enclosingContext = PsiTreeUtil.getParentOfType(enclosingContext == null ? ref : enclosingContext, PsiMethod.class,
                                                       PsiField.class, PsiClassInitializer.class);
        parentClass = enclosingContext == null ? null : enclosingContext.getContainingClass();
      }
      while (parentClass instanceof PsiAnonymousClass);

      ExpectedTypeInfo[] expectedTypes = CreateFromUsageUtils.guessExpectedTypes(ref, false);

      PsiField field = factory.createField(fieldName, PsiTypes.intType());
      if (!targetClass.isInterface() && CreateFromUsageUtils.shouldCreateStaticMember(ref, targetClass)) {
        PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true);
      }
      if (shouldCreateFinalMember(ref, targetClass)) {
        PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
      }
      field = CreateFieldFromUsageHelper.insertField(targetClass, field, ref);
      if (field == null) return;
      CreateFromUsageUtils.setupVisibility(parentClass, targetClass, field.getModifierList());

      RecordingTemplateBuilder fields = new RecordingTemplateBuilder();
      PsiTypeElement typeElement = field.getTypeElement();
      if (typeElement != null) {
        new GuessTypeParameters(project, factory, fields, CreateFromUsageUtils.getTargetSubstitutor(ref))
          .setupTypeElement(typeElement, expectedTypes, ref, targetClass);
      }

      SmartPsiElementPointer<PsiField> fieldPointer = SmartPointerManager.createPointer(field);
      // The target class can be in another file. Move the caret first, so the updater tracks that file.
      updater.moveCaretTo(field);
      ModTemplateBuilder builder = fields.isEmpty() ? null : updater.templateBuilder();
      if (builder != null) {
        fields.flushTo(builder);
        PsiDocumentManager.getInstance(project).commitDocument(updater.getDocument());
      }
      PsiField liveField = fieldPointer.getElement();
      if (liveField == null) return;
      // The caret goes before the semicolon, so the user can add an initializer.
      int caretOffset = liveField.getTextRange().getEndOffset() - 1;
      if (builder == null) {
        updater.moveCaretTo(caretOffset);
        return;
      }
      builder.finishAt(caretOffset);
      builder.onTemplateFinished(_ -> reformatField(myTargetClass, fieldName));
    }
  }

  /**
   * Reformats the new field after the template ends, because the template changes the type of the field.
   */
  private static @NotNull ModCommand reformatField(@NotNull SmartPsiElementPointer<PsiClass> targetClass, @NotNull String fieldName) {
    PsiClass psiClass = targetClass.getElement();
    PsiField field = psiClass == null ? null : psiClass.findFieldByName(fieldName, false);
    if (field == null) return ModCommand.nop();
    return ModCommand.psiUpdate(field, (writableField, updater) -> {
      PsiElement formatted = CodeStyleManager.getInstance(writableField.getProject()).reformat(writableField);
      updater.moveCaretTo(formatted.getTextRange().getEndOffset() - 1);
    });
  }

  public static boolean shouldCreateFinalMember(@NotNull PsiReferenceExpression ref, @NotNull PsiClass targetClass) {
    if (!PsiTreeUtil.isAncestor(targetClass, ref, true)) {
      return false;
    }
    final PsiElement element = PsiTreeUtil.getParentOfType(ref, PsiClassInitializer.class, PsiMethod.class, PsiField.class);
    return element instanceof PsiClassInitializer || element instanceof PsiMethod method && method.isConstructor();
  }
}
