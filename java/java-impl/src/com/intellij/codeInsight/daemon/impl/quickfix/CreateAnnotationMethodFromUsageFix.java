// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.ModTemplateBuilder;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Creates a method in the annotation type for an unresolved attribute name of an annotation.
 */
public final class CreateAnnotationMethodFromUsageFix extends PsiUpdateModCommandAction<PsiNameValuePair> {
  public CreateAnnotationMethodFromUsageFix(@NotNull PsiNameValuePair valuePair) {
    super(valuePair);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.method.from.usage.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiNameValuePair pair) {
    if (isResolved(pair)) return null;
    String name = getMethodName(pair);
    if (!PsiNameHelper.getInstance(context.project()).isIdentifier(name, PsiUtil.getLanguageLevel(pair))) return null;
    if (getAnnotationValueType(pair.getValue()) == null) return null;
    if (getTargetClass(pair, context.project()) == null) return null;
    return Presentation.of(JavaBundle.message("intention.create.annotation.method.from.usage", name));
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiNameValuePair pair, @NotNull ModPsiUpdater updater) {
    if (isResolved(pair)) return;
    PsiType type = getAnnotationValueType(pair.getValue());
    if (type == null) return;
    PsiClass targetClass = getTargetClass(pair, context.project());
    if (targetClass == null) return;
    targetClass = updater.getWritable(targetClass);
    Project project = context.project();

    PsiMethod method = JavaPsiFacade.getElementFactory(project).createMethod(getMethodName(pair), PsiTypes.voidType());
    method = (PsiMethod)targetClass.add(method);
    PsiCodeBlock body = method.getBody();
    assert body != null;
    body.delete();

    PsiElement guesserContext = PsiTreeUtil.getParentOfType(pair, PsiClass.class, PsiMethod.class);
    ExpectedTypeInfo[] expectedTypes =
      {ExpectedTypesProvider.createInfo(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailTypes.noneType())};
    RecordingTemplateBuilder fields = new RecordingTemplateBuilder();
    PsiTypeElement returnTypeElement = method.getReturnTypeElement();
    if (returnTypeElement != null) {
      new GuessTypeParameters(project, JavaPsiFacade.getElementFactory(project), fields, PsiSubstitutor.EMPTY)
        .setupTypeElement(returnTypeElement, expectedTypes, guesserContext, targetClass);
    }

    SmartPsiElementPointer<PsiMethod> methodPointer = SmartPointerManager.createPointer(method);
    // The target class can be in another file. Move the caret first, so the updater tracks that file.
    updater.moveCaretTo(method);
    if (fields.isEmpty()) {
      updater.moveCaretTo(method.getTextRange().getEndOffset());
      return;
    }
    ModTemplateBuilder builder = updater.templateBuilder();
    fields.flushTo(builder);
    PsiDocumentManager.getInstance(project).commitDocument(updater.getDocument());
    PsiMethod liveMethod = methodPointer.getElement();
    if (liveMethod != null) {
      builder.finishAt(liveMethod.getTextRange().getEndOffset());
    }
  }

  private static boolean isResolved(@NotNull PsiNameValuePair pair) {
    PsiReference reference = pair.getReference();
    return reference != null && reference.resolve() != null;
  }

  private static @NotNull String getMethodName(@NotNull PsiNameValuePair pair) {
    return ObjectUtils.notNull(pair.getName(), PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
  }

  /**
   * @return the annotation type which can get the new method, as a list with one element, or an empty list
   */
  private static @Nullable PsiClass getTargetClass(@NotNull PsiNameValuePair pair, @NotNull Project project) {
    PsiClass psiClass = ContainerUtil.getOnlyItem(CreateFromUsageUtils.getTargetClasses(pair, true, BaseIntentionAction::canModify));
    if (psiClass == null || !BaseIntentionAction.canModify(psiClass)) return null;
    return psiClass;
  }

  public static @Nullable PsiType getAnnotationValueType(PsiAnnotationMemberValue value) {
    PsiType type = null;
    if (value instanceof PsiExpression expression) {
      type = expression.getType();
    } else if (value instanceof PsiArrayInitializerMemberValue arrayValue) {
      final PsiAnnotationMemberValue[] initializers = arrayValue.getInitializers();
      PsiType currentType = null;
      for (PsiAnnotationMemberValue initializer : initializers) {
        if (initializer instanceof PsiArrayInitializerMemberValue) return null;
        if (!(initializer instanceof PsiExpression expression)) return null;
        final PsiType psiType = expression.getType();
        if (psiType != null) {
          if (currentType == null) {
            currentType = psiType;
          } else {
            if (!TypeConversionUtil.isAssignable(currentType, psiType)) {
              if (TypeConversionUtil.isAssignable(psiType, currentType)) {
                currentType = psiType;
              } else {
                return null;
              }
            }
          }
        }
      }
      if (currentType != null) {
        type = currentType.createArrayType();
      }
    }
    if (type != null && PsiTypesUtil.isValidAnnotationMethodType(type)) {
      return type;
    }
    return null;
  }
}
