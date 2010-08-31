/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.util.MethodParenthesesHandler;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.JavaElementLookupRenderer;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class JavaMethodCallElement extends LookupItem<PsiMethod> implements TypedLookupItem, StaticallyImportable {
  private static final Key<PsiSubstitutor> INFERENCE_SUBSTITUTOR = Key.create("INFERENCE_SUBSTITUTOR");
  private final PsiClass myContainingClass;
  private final PsiMethod myMethod;
  private final boolean myCanImportStatic;
  private boolean myShouldImportStatic;

  public JavaMethodCallElement(@NotNull PsiMethod method) {
    this(method, false);
  }

  public JavaMethodCallElement(PsiMethod method, boolean canImportStatic) {
    super(method, method.getName());
    myMethod = method;
    myContainingClass = method.getContainingClass();
    myCanImportStatic = canImportStatic;
    PsiType type = method.getReturnType();
    setTailType(PsiType.VOID.equals(type) ? TailType.SEMICOLON : TailType.NONE);
  }

  public PsiType getType() {
    return getSubstitutor().substitute(getInferenceSubstitutor().substitute(getObject().getReturnType()));
  }

  public void setInferenceSubstitutor(@NotNull final PsiSubstitutor substitutor) {
    setAttribute(INFERENCE_SUBSTITUTOR, substitutor);
  }

  @NotNull
  public PsiSubstitutor getSubstitutor() {
    final PsiSubstitutor substitutor = (PsiSubstitutor)getAttribute(SUBSTITUTOR);
    return substitutor == null ? PsiSubstitutor.EMPTY : substitutor;
  }

  @NotNull
  public PsiSubstitutor getInferenceSubstitutor() {
    final PsiSubstitutor substitutor = getAttribute(INFERENCE_SUBSTITUTOR);
    return substitutor == null ? PsiSubstitutor.EMPTY : substitutor;
  }

  @Override
  public void setShouldBeImported(boolean shouldImportStatic) {
    assert myCanImportStatic;
    myShouldImportStatic = shouldImportStatic;
  }

  @Override
  public boolean canBeImported() {
    return myCanImportStatic;
  }

  @Override
  public boolean willBeImported() {
    return myShouldImportStatic;
  }

  @Override
  public void handleInsert(InsertionContext context) {
    final Document document = context.getDocument();
    final PsiFile file = context.getFile();
    final PsiMethod method = getObject();

    final LookupElement[] allItems = context.getElements();
    final boolean overloadsMatter = allItems.length == 1 && getUserData(FORCE_SHOW_SIGNATURE_ATTR) == null;
    final boolean hasParams = MethodParenthesesHandler.hasParams(this, allItems, overloadsMatter, method);
    JavaCompletionUtil.insertParentheses(context, this, overloadsMatter, hasParams);

    final int startOffset = context.getStartOffset();
    final OffsetKey refStart = context.trackOffset(startOffset, true);
    if (SmartCompletionDecorator.hasUnboundTypeParams(method)) {
      qualifyMethodCall(file, startOffset, document);
      insertExplicitTypeParameters(context, refStart);
    }
    else if (myCanImportStatic || getAttribute(FORCE_QUALIFY) != null) {
      context.commitDocument();
      if (myCanImportStatic && myShouldImportStatic) {
        final PsiReferenceExpression ref = PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiReferenceExpression.class, false);
        if (ref != null) {
          ref.bindToElementViaStaticImport(myContainingClass);
        }
        return;
      }

      qualifyMethodCall(file, startOffset, document);
    }

    final PsiType type = method.getReturnType();
    if (context.getCompletionChar() == '!' && type != null && PsiType.BOOLEAN.isAssignableFrom(type)) {
      context.commitDocument();
      final int offset = context.getOffset(refStart);
      final PsiMethodCallExpression methodCall = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiMethodCallExpression.class, false);
      if (methodCall != null) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EXCLAMATION_FINISH);
        document.insertString(methodCall.getTextRange().getStartOffset(), "!");
      }
    }

  }

  private boolean insertExplicitTypeParameters(InsertionContext context, OffsetKey refStart) {
    context.commitDocument();

    PsiExpression expression = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getOffset(refStart), PsiExpression.class, false);
    if (expression == null) return true;

    final ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getExpectedTypes(expression, true);
    if (expectedTypes == null) return true;

    for (final ExpectedTypeInfo type : expectedTypes) {
      if (type.isInsertExplicitTypeParams()) {
        final String typeParams = getTypeParamsText(type.getType());
        if (typeParams == null) {
          return true;
        }

        context.getDocument().insertString(context.getOffset(refStart), typeParams);

        JavaCompletionUtil.shortenReference(context.getFile(), context.getOffset(refStart));

        break;
      }
    }

    return true;
  }

  private void qualifyMethodCall(PsiFile file, final int startOffset, final Document document) {
    final PsiReference reference = file.findReferenceAt(startOffset);
    if (reference instanceof PsiReferenceExpression && ((PsiReferenceExpression)reference).isQualified()) {
      return;
    }

    final PsiMethod method = getObject();
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      document.insertString(startOffset, "this.");

      if (method.getManager().areElementsEquivalent(myContainingClass, PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiClass.class, false))) {
        return;
      }
    }

    document.insertString(startOffset, ".");
    JavaCompletionUtil.insertClassReference(myContainingClass, file, startOffset);
  }

  @Nullable
  private String getTypeParamsText(PsiType expectedType) {
    final PsiMethod method = getObject();
    final PsiSubstitutor substitutor = SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor(method, expectedType);
    assert substitutor != null;
    final PsiTypeParameter[] parameters = method.getTypeParameters();
    assert parameters.length > 0;
    final StringBuilder builder = new StringBuilder("<");
    boolean first = true;
    for (final PsiTypeParameter parameter : parameters) {
      if (!first) builder.append(", ");
      first = false;
      PsiType type = substitutor.substitute(parameter);
      if (type instanceof PsiWildcardType) {
        type = ((PsiWildcardType)type).getExtendsBound();
      }

      if (type == null || type instanceof PsiCapturedWildcardType) return null;

      final String text = type.getCanonicalText();
      if (text.indexOf('?') >= 0) return null;

      builder.append(text);
    }
    return builder.append(">").toString();
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    final String className = myContainingClass.getName();

    presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(this, presentation.isReal()));

    final String methodName = myMethod.getName();
    final boolean qualify = myCanImportStatic && !myShouldImportStatic || getAttribute(FORCE_QUALIFY) != null;
    if (qualify && StringUtil.isNotEmpty(className)) {
      presentation.setItemText(className + "." + methodName);
    } else {
      presentation.setItemText(methodName);
    }

    presentation.setStrikeout(JavaElementLookupRenderer.isToStrikeout(this));
    presentation.setItemTextBold(getAttribute(HIGHLIGHTED_ATTR) != null);


    final String params = PsiFormatUtil.formatMethod(myMethod, PsiSubstitutor.EMPTY,
                                                     PsiFormatUtil.SHOW_PARAMETERS,
                                                     PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE);
    if (myShouldImportStatic && StringUtil.isNotEmpty(className)) {
      presentation.setTailText(params + " in " + className);
    } else {
      presentation.setTailText(params);
    }

    final PsiType type = myMethod.getReturnType();
    if (type != null) {
      presentation.setTypeText(getSubstitutor().substitute(type).getPresentableText());
    }
  }
}
