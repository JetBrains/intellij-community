/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.light;

import com.intellij.psi.PsiMirrorElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public class LightParameterListWrapper extends LightElement implements PsiParameterList, PsiMirrorElement {

  private final @NotNull PsiParameterList myDelegate;
  private final @NotNull PsiSubstitutor mySubstitutor;

  public LightParameterListWrapper(@NotNull PsiParameterList delegate, @NotNull PsiSubstitutor substitutor) {
    super(delegate.getManager(), delegate.getLanguage());
    myDelegate = delegate;
    mySubstitutor = substitutor;
  }

  @NotNull
  @Override
  public PsiParameterList getPrototype() {
    return myDelegate;
  }

  @Override
  @NotNull
  public PsiParameter[] getParameters() {
    return mySubstitutor == PsiSubstitutor.EMPTY
           ? myDelegate.getParameters()
           : ContainerUtil.map2Array(myDelegate.getParameters(), PsiParameter.class, new Function<PsiParameter, PsiParameter>() {
             @Override
             public PsiParameter fun(PsiParameter parameter) {
               return new LightParameterWrapper(parameter, mySubstitutor);
             }
           });
  }

  @Override
  public int getParameterIndex(@NotNull PsiParameter parameter) {
    if (parameter instanceof LightParameterWrapper) {
      parameter = ((LightParameterWrapper)parameter).getPrototype();
    }
    return myDelegate.getParameterIndex(parameter);
  }

  @Override
  public int getParametersCount() {
    return myDelegate.getParametersCount();
  }

  @Override
  public String toString() {
    return "List PSI parameter list wrapper: " + myDelegate;
  }
}
