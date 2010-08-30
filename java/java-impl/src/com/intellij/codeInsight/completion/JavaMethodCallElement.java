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

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.simple.PsiMethodInsertHandler;
import com.intellij.codeInsight.lookup.DefaultLookupItemRenderer;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.codeInsight.lookup.impl.JavaElementLookupRenderer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

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
    PsiMethodInsertHandler.INSTANCE.handleInsert(context, this);
    if (myCanImportStatic) {
      final int startOffset = context.getStartOffset();
      final PsiFile file = context.getFile();
      PsiDocumentManager.getInstance(file.getProject()).commitDocument(context.getDocument());
      if (myShouldImportStatic) {
        final PsiReferenceExpression ref = PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiReferenceExpression.class, false);
        if (ref != null) {
          ref.bindToElementViaStaticImport(myContainingClass);
        }
      } else {
        context.getDocument().insertString(startOffset, ".");
        JavaCompletionUtil.insertClassReference(myContainingClass, file, startOffset);
      }
    }
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
