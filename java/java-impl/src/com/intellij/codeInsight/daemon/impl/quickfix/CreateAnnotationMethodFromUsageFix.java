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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil;
import com.intellij.codeInsight.intention.CreateFromUsage;
import com.intellij.codeInsight.intention.JvmCommonIntentionActionsFactory;
import com.intellij.codeInsight.intention.impl.JavaCommonIntentionActionsFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import kotlin.collections.ArraysKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

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

    if (name == null || !PsiNameHelper.getInstance(call.getProject()).isIdentifier(name)) return false;
    if (getAnnotationValueType(call.getValue()) == null) return false;
    setText(QuickFixBundle.message("create.method.from.usage.text", name));
    return true;
  }

  @Override
  protected PsiElement getElement() {
    final PsiNameValuePair call = getNameValuePair();
    if (call == null || !call.getManager().isInProject(call)) return null;
    return call;
  }

  @Override
  protected void invokeImpl(final PsiClass targetClass) {
    if (targetClass == null) return;

    PsiNameValuePair nameValuePair = getNameValuePair();
    if (nameValuePair == null || isValidElement(nameValuePair)) return;

    final String methodName = nameValuePair.getName();
    LOG.assertTrue(methodName != null);

    final PsiType type = getAnnotationValueType(nameValuePair.getValue());
    LOG.assertTrue(type != null);
    final ExpectedTypeInfo[] expectedTypes =
      new ExpectedTypeInfo[]{ExpectedTypesProvider.createInfo(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.NONE)};

    JvmCommonIntentionActionsFactory actionsFactory = JvmCommonIntentionActionsFactory.forLanguage(targetClass.getLanguage());
    if (actionsFactory != null && !(actionsFactory instanceof JavaCommonIntentionActionsFactory)) {
      CreateFromUsage.MethodInfo methodInfo = new CreateFromUsage.MethodInfo(
        targetClass,
        methodName,
        Collections.emptyList(),
        new CreateFromUsage.TypeInfo(ArraysKt.toList(expectedTypes)),
        Collections.emptyList()
      );
      CreateFromUsageUtils.invokeActionInTargetEditor(targetClass, () -> actionsFactory.createGenerateMethodFromUsageActions(methodInfo));
      return;
    }

    final PsiElementFactory factory = JavaPsiFacade.getInstance(nameValuePair.getProject()).getElementFactory();
    PsiMethod method = factory.createMethod(methodName, PsiType.VOID);

    method = (PsiMethod)targetClass.add(method);

    PsiCodeBlock body = method.getBody();
    assert body != null;
    body.delete();

    final PsiElement context = PsiTreeUtil.getParentOfType(nameValuePair, PsiClass.class, PsiMethod.class);

    CreateMethodFromUsageFix.doCreate(targetClass, method, true, ContainerUtil.map2List(PsiExpression.EMPTY_ARRAY, Pair.createFunction(null)),
                                      getTargetSubstitutor(nameValuePair), expectedTypes, context);
  }

  @Nullable
  public static PsiType getAnnotationValueType(PsiAnnotationMemberValue value) {
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
    if (type != null && type.accept(AnnotationsHighlightUtil.AnnotationReturnTypeVisitor.INSTANCE).booleanValue()) {
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
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.method.from.usage.family");
  }

  @Nullable
  protected PsiNameValuePair getNameValuePair() {
    return myNameValuePair.getElement();
  }
}
