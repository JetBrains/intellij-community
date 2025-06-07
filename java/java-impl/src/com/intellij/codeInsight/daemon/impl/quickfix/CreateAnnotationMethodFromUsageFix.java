// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class CreateAnnotationMethodFromUsageFix extends CreateFromUsageBaseFix {
  private static final Logger LOG = Logger.getInstance(CreateAnnotationMethodFromUsageFix.class);

  private final SmartPsiElementPointer<PsiNameValuePair> myNameValuePair;

  public CreateAnnotationMethodFromUsageFix(@NotNull PsiNameValuePair valuePair) {
    myNameValuePair = SmartPointerManager.getInstance(valuePair.getProject()).createSmartPsiElementPointer(valuePair);
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    final PsiNameValuePair call = getNameValuePair();
    if (call == null || !call.isValid()) return false;
    String name = call.getName();
    if (name == null) name = PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME;

    if (!PsiNameHelper.getInstance(call.getProject()).isIdentifier(name)) return false;
    if (getAnnotationValueType(call.getValue()) == null) return false;
    setText(JavaBundle.message("intention.create.annotation.method.from.usage", name));
    return true;
  }

  @Override
  protected PsiElement getElement() {
    final PsiNameValuePair call = getNameValuePair();
    if (call == null || !call.getManager().isInProject(call)) return null;
    return call;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    chooseTargetClass(project, editor, this::invokeImpl);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiNameValuePair nameValuePair = getNameValuePair();
    List<PsiClass> classes = filterTargetClasses(nameValuePair, project);
    if (nameValuePair == null || classes.isEmpty()) return IntentionPreviewInfo.EMPTY;
    final PsiType type = getAnnotationValueType(nameValuePair.getValue());
    PsiClass targetClass = classes.get(0);
    String methodName = ObjectUtils.notNull(nameValuePair.getName(), PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, targetClass.getName(), "",
                                               Objects.requireNonNull(type).getPresentableText() + " " + methodName + "();");
  }

  private void invokeImpl(@NotNull PsiClass targetClass) {
    PsiNameValuePair nameValuePair = getNameValuePair();
    if (nameValuePair == null || isValidElement(nameValuePair)) return;

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(nameValuePair.getProject());

    final String methodName = ObjectUtils.notNull(nameValuePair.getName(), PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
    LOG.assertTrue(methodName != null);

    PsiMethod method = factory.createMethod(methodName, PsiTypes.voidType());

    method = (PsiMethod)targetClass.add(method);

    PsiCodeBlock body = method.getBody();
    assert body != null;
    body.delete();
    
    final PsiElement context = PsiTreeUtil.getParentOfType(nameValuePair, PsiClass.class, PsiMethod.class);

    final PsiType type = getAnnotationValueType(nameValuePair.getValue());
    LOG.assertTrue(type != null);
    final ExpectedTypeInfo[] expectedTypes =
      new ExpectedTypeInfo[]{ExpectedTypesProvider.createInfo(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailTypes.noneType())};
    CreateMethodFromUsageFix.doCreate(targetClass, method, true, Collections.emptyList(), 
                                      getTargetSubstitutor(nameValuePair), expectedTypes, context);
  }

  public static @Nullable PsiType getAnnotationValueType(PsiAnnotationMemberValue value) {
    PsiType type = null;
    if (value instanceof PsiExpression) {
      type = ((PsiExpression)value).getType();
    } else if (value instanceof PsiArrayInitializerMemberValue) {
      final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)value).getInitializers();
      PsiType currentType = null;
      for (PsiAnnotationMemberValue initializer : initializers) {
        if (initializer instanceof PsiArrayInitializerMemberValue) return null;
        if (!(initializer instanceof PsiExpression)) return null;
        final PsiType psiType = ((PsiExpression)initializer).getType();
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


  @Override
  protected boolean isValidElement(PsiElement element) {
    final PsiReference reference = element.getReference();
    return reference != null && reference.resolve() != null;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.method.from.usage.family");
  }

  protected @Nullable PsiNameValuePair getNameValuePair() {
    return myNameValuePair.getElement();
  }
}
