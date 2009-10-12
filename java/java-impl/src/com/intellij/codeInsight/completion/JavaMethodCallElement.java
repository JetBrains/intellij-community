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

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.simple.PsiMethodInsertHandler;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;

/**
 * @author peter
 */
public class JavaMethodCallElement extends LookupItem<PsiMethod> {

  public JavaMethodCallElement(PsiMethod method) {
    super(method, method.getName());
    PsiType type = method.getReturnType();
    setTailType(PsiType.VOID.equals(type) ? TailType.SEMICOLON : TailType.NONE);
    setInsertHandler(new PsiMethodInsertHandler(method));
  }

}
