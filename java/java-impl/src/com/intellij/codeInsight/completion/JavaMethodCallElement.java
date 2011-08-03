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
import com.intellij.codeInsight.completion.util.MethodParenthesesHandler;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.JavaElementLookupRenderer;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.ClassConditionKey;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class JavaMethodCallElement extends LookupItem<PsiMethod> implements TypedLookupItem, StaticallyImportable {
  public static final ClassConditionKey<JavaMethodCallElement> CLASS_CONDITION_KEY = ClassConditionKey.create(JavaMethodCallElement.class);
  private static final Key<PsiSubstitutor> INFERENCE_SUBSTITUTOR = Key.create("INFERENCE_SUBSTITUTOR");
  @Nullable private final PsiClass myContainingClass;
  private final PsiMethod myMethod;
  private final MemberLookupHelper myHelper;

  public JavaMethodCallElement(@NotNull PsiMethod method) {
    this(method, false, false);
  }

  public JavaMethodCallElement(PsiMethod method, boolean canImportStatic, boolean mergedOverloads) {
    super(method, method.getName());
    myMethod = method;
    myContainingClass = method.getContainingClass();
    myHelper = canImportStatic ? new MemberLookupHelper(method, myContainingClass, false, mergedOverloads) : null;
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
    myHelper.setShouldBeImported(shouldImportStatic);
  }

  @Override
  public boolean canBeImported() {
    return myHelper != null;
  }

  @Override
  public boolean willBeImported() {
    return canBeImported() && myHelper.willBeImported();
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
    if (shouldInsertTypeParameters(context, startOffset)) {
      qualifyMethodCall(file, startOffset, document);
      insertExplicitTypeParameters(context, refStart);
    }
    else if (myHelper != null || getAttribute(FORCE_QUALIFY) != null) {
      context.commitDocument();
      if (myHelper != null && willBeImported()) {
        final PsiReferenceExpression ref = PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiReferenceExpression.class, false);
        if (ref != null && myContainingClass != null) {
          ref.bindToElementViaStaticImport(myContainingClass);
        }
        return;
      }

      qualifyMethodCall(file, startOffset, document);
    }

    final PsiType type = method.getReturnType();
    if (context.getCompletionChar() == '!' && type != null && PsiType.BOOLEAN.isAssignableFrom(type)) {
      context.setAddCompletionChar(false);
      context.commitDocument();
      final int offset = context.getOffset(refStart);
      final PsiMethodCallExpression methodCall = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiMethodCallExpression.class, false);
      if (methodCall != null) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EXCLAMATION_FINISH);
        document.insertString(methodCall.getTextRange().getStartOffset(), "!");
      }
    }

  }

  private boolean shouldInsertTypeParameters(InsertionContext context, int offset) {
    final PsiElement leaf = context.getFile().findElementAt(context.getStartOffset());
    if (PsiTreeUtil.getParentOfType(leaf, PsiExpressionList.class, true, PsiCodeBlock.class, PsiModifierListOwner.class) == null) {
      if (PsiTreeUtil.getParentOfType(leaf, PsiConditionalExpression.class, true, PsiCodeBlock.class, PsiModifierListOwner.class) == null) {
        return false;
      }
    }
    if (leaf != null) {
      final PsiElement parent = leaf.getParent();
      if (parent instanceof PsiReferenceExpression && ((PsiReferenceExpression)parent).getTypeParameters().length > 0) {
        return false;
      }
    }

    return SmartCompletionDecorator.hasUnboundTypeParams(getObject(), getExpectedTypeForExplicitTypeParameters(context, offset));
  }

  @Nullable
  private static PsiType getExpectedTypeForExplicitTypeParameters(InsertionContext context, final int offset) {
    context.commitDocument();

    PsiExpression expression = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), offset, PsiExpression.class, false);
    if (expression == null) return null;

    for (final ExpectedTypeInfo type : ExpectedTypesProvider.getExpectedTypes(expression, true)) {
      if (type.isInsertExplicitTypeParams()) {
        return type.getType();
      }
    }
    return null;
  }

  private void insertExplicitTypeParameters(InsertionContext context, OffsetKey refStart) {
    context.commitDocument();

    PsiType psiType = getExpectedTypeForExplicitTypeParameters(context, context.getOffset(refStart));
    if (psiType != null) {
      final String typeParams = getTypeParamsText(psiType);
      if (typeParams != null) {
        context.getDocument().insertString(context.getOffset(refStart), typeParams);
        JavaCompletionUtil.shortenReference(context.getFile(), context.getOffset(refStart));
      }
    }
  }

  private void qualifyMethodCall(PsiFile file, final int startOffset, final Document document) {
    final PsiReference reference = file.findReferenceAt(startOffset);
    if (reference instanceof PsiReferenceExpression && ((PsiReferenceExpression)reference).isQualified()) {
      return;
    }

    final PsiMethod method = getObject();
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      document.insertString(startOffset, "this.");
      return;
    }

    if (myContainingClass == null) return;

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
  public LookupItem<PsiMethod> forceQualify() {
    if (myContainingClass != null) {
      String className = myContainingClass.getName();
      if (className != null) {
        addLookupStrings(className + "." + myMethod.getName());
      }
    }
    return super.forceQualify();
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(this, presentation.isReal()));

    presentation.setStrikeout(JavaElementLookupRenderer.isToStrikeout(this));
    presentation.setItemTextBold(getAttribute(HIGHLIGHTED_ATTR) != null);

    MemberLookupHelper helper = myHelper != null ? myHelper : new MemberLookupHelper(myMethod, myContainingClass, false, false);
    final Boolean qualify = getAttribute(FORCE_QUALIFY) != null ? Boolean.TRUE : myHelper == null ? Boolean.FALSE : null;
    helper.renderElement(presentation, qualify, getSubstitutor());
  }
}
