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
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class JavaMethodCallElement extends LookupItem<PsiMethod> implements TypedLookupItem {
  private static final Key<PsiSubstitutor> INFERENCE_SUBSTITUTOR = Key.create("INFERENCE_SUBSTITUTOR");

  public JavaMethodCallElement(PsiMethod method) {
    super(method, method.getName());
    PsiType type = method.getReturnType();
    setTailType(PsiType.VOID.equals(type) ? TailType.SEMICOLON : TailType.NONE);
    setInsertHandler(PsiMethodInsertHandler.INSTANCE);
  }

  public PsiType getType() {
    return getSubstitutor().substitute(getInferenceSubstitutor().substitute(getObject().getReturnType()));

  }

  public void setInferenceSubstitutor(@NotNull final PsiSubstitutor substitutor) {
    setAttribute(INFERENCE_SUBSTITUTOR, substitutor);
  }

  @NotNull
  public PsiSubstitutor getSubstitutor() {
    final PsiSubstitutor substitutor = (PsiSubstitutor)getAttribute(LookupItem.SUBSTITUTOR);
    return substitutor == null ? PsiSubstitutor.EMPTY : substitutor;
  }

  public void setSubstitutor(@NotNull PsiSubstitutor substitutor) {
    setAttribute(SUBSTITUTOR, substitutor);
  }

  @NotNull
  public PsiSubstitutor getInferenceSubstitutor() {
    final PsiSubstitutor substitutor = getAttribute(INFERENCE_SUBSTITUTOR);
    return substitutor == null ? PsiSubstitutor.EMPTY : substitutor;
  }
}
