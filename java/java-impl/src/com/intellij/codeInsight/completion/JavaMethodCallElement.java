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

import com.intellij.codeInsight.completion.util.MethodParenthesesHandler;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.JavaElementLookupRenderer;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.ClassConditionKey;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class JavaMethodCallElement extends LookupItem<PsiMethod> implements TypedLookupItem, StaticallyImportable {
  public static final ClassConditionKey<JavaMethodCallElement> CLASS_CONDITION_KEY = ClassConditionKey.create(JavaMethodCallElement.class);
  @Nullable private final PsiClass myContainingClass;
  private final PsiMethod myMethod;
  private final MemberLookupHelper myHelper;
  private PsiSubstitutor myQualifierSubstitutor = PsiSubstitutor.EMPTY;
  private PsiSubstitutor myInferenceSubstitutor = PsiSubstitutor.EMPTY;
  private boolean myMayNeedExplicitTypeParameters;
  private String myForcedQualifier = "";

  public JavaMethodCallElement(@NotNull PsiMethod method) {
    this(method, method.getName());
  }

  public JavaMethodCallElement(@NotNull PsiMethod method, String methodName) {
    super(method, methodName);
    myMethod = method;
    myHelper = null;
    myContainingClass = method.getContainingClass();
  }

  public JavaMethodCallElement(PsiMethod method, boolean shouldImportStatic, boolean mergedOverloads) {
    super(method, method.getName());
    myMethod = method;
    myContainingClass = method.getContainingClass();
    myHelper = new MemberLookupHelper(method, myContainingClass, shouldImportStatic, mergedOverloads);
    if (!shouldImportStatic) {
      if (myContainingClass != null) {
        String className = myContainingClass.getName();
        if (className != null) {
          addLookupStrings(className + "." + myMethod.getName());
        }
      }
    }
  }

  void setForcedQualifier(@NotNull String forcedQualifier) {
    myForcedQualifier = forcedQualifier;
    setLookupString(forcedQualifier + getLookupString());
  }

  @Override
  public PsiType getType() {
    return getSubstitutor().substitute(getInferenceSubstitutor().substitute(getObject().getReturnType()));
  }

  public void setInferenceSubstitutor(@NotNull final PsiSubstitutor substitutor, PsiElement place) {
    myInferenceSubstitutor = substitutor;
    myMayNeedExplicitTypeParameters = mayNeedTypeParameters(place);
  }

  public JavaMethodCallElement setQualifierSubstitutor(@NotNull PsiSubstitutor qualifierSubstitutor) {
    myQualifierSubstitutor = qualifierSubstitutor;
    return this;
  }

  @NotNull
  public PsiSubstitutor getSubstitutor() {
    return myQualifierSubstitutor;
  }

  @NotNull
  public PsiSubstitutor getInferenceSubstitutor() {
    return myInferenceSubstitutor;
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JavaMethodCallElement)) return false;
    if (!super.equals(o)) return false;

    return myInferenceSubstitutor.equals(((JavaMethodCallElement)o).myInferenceSubstitutor);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myInferenceSubstitutor.hashCode();
    return result;
  }

  @Override
  public void handleInsert(InsertionContext context) {
    final Document document = context.getDocument();
    final PsiFile file = context.getFile();
    final PsiMethod method = getObject();

    final LookupElement[] allItems = context.getElements();
    final boolean overloadsMatter = allItems.length == 1 && getUserData(JavaCompletionUtil.FORCE_SHOW_SIGNATURE_ATTR) == null;
    final boolean hasParams = MethodParenthesesHandler.hasParams(this, allItems, overloadsMatter, method);
    JavaCompletionUtil.insertParentheses(context, this, overloadsMatter, hasParams);

    final int startOffset = context.getStartOffset();
    final OffsetKey refStart = context.trackOffset(startOffset, true);
    if (shouldInsertTypeParameters() && mayNeedTypeParameters(context.getFile().findElementAt(context.getStartOffset()))) {
      qualifyMethodCall(file, startOffset, document);
      insertExplicitTypeParameters(context, refStart);
    }
    else if (myHelper != null) {
      context.commitDocument();
      if (willBeImported()) {
        final PsiReferenceExpression ref = PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiReferenceExpression.class, false);
        if (ref != null && myContainingClass != null && !ref.isReferenceTo(method)) {
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

  private boolean shouldInsertTypeParameters() {
    return myMayNeedExplicitTypeParameters && !getInferenceSubstitutor().equals(PsiSubstitutor.EMPTY) && myMethod.getParameterList().getParametersCount() == 0;
  }

  public static boolean mayNeedTypeParameters(@Nullable final PsiElement leaf) {
    if (PsiTreeUtil.getParentOfType(leaf, PsiExpressionList.class, true, PsiCodeBlock.class, PsiModifierListOwner.class) == null) {
      if (PsiTreeUtil.getParentOfType(leaf, PsiConditionalExpression.class, true, PsiCodeBlock.class, PsiModifierListOwner.class) == null) {
        return false;
      }
    }

    if (PsiUtil.getLanguageLevel(leaf).isAtLeast(LanguageLevel.JDK_1_8)) return false;

    final PsiElement parent = leaf.getParent();
    if (parent instanceof PsiReferenceExpression && ((PsiReferenceExpression)parent).getTypeParameters().length > 0) {
      return false;
    }
    return true;
  }

  private void insertExplicitTypeParameters(InsertionContext context, OffsetKey refStart) {
    context.commitDocument();

    final String typeParams = getTypeParamsText(false);
    if (typeParams != null) {
      context.getDocument().insertString(context.getOffset(refStart), typeParams);
      JavaCompletionUtil.shortenReference(context.getFile(), context.getOffset(refStart));
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
  private String getTypeParamsText(boolean presentable) {
    final PsiMethod method = getObject();
    final PsiSubstitutor substitutor = getInferenceSubstitutor();
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
      if (type.equals(TypeConversionUtil.typeParameterErasure(parameter))) return null;

      final String text = presentable ? type.getPresentableText() : type.getCanonicalText();
      if (text.indexOf('?') >= 0) return null;

      builder.append(text);
    }
    return builder.append(">").toString();
  }

  @Override
  public boolean isValid() {
    return super.isValid() && myInferenceSubstitutor.isValid() && getSubstitutor().isValid();
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(this, presentation.isReal()));

    presentation.setStrikeout(JavaElementLookupRenderer.isToStrikeout(this));

    MemberLookupHelper helper = myHelper != null ? myHelper : new MemberLookupHelper(myMethod, myContainingClass, false, false);
    helper.renderElement(presentation, myHelper != null, myHelper != null && !myHelper.willBeImported(), getSubstitutor());
    if (!myForcedQualifier.isEmpty()) {
      presentation.setItemText(myForcedQualifier + presentation.getItemText());
    }

    if (shouldInsertTypeParameters()) {
      String typeParamsText = getTypeParamsText(true);
      if (typeParamsText != null) {
        if (typeParamsText.length() > 10) {
          typeParamsText = typeParamsText.substring(0, 10) + "...>";
        }

        String itemText = presentation.getItemText();
        assert itemText != null;
        int i = itemText.indexOf('.');
        if (i > 0) {
          presentation.setItemText(itemText.substring(0, i + 1) + typeParamsText + itemText.substring(i + 1));
        }
      }
    }
    
  }
}
