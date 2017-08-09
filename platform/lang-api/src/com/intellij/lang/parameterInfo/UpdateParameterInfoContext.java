/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.lang.parameterInfo;

import com.intellij.psi.PsiElement;

public interface UpdateParameterInfoContext extends ParameterInfoContext {
  void removeHint();

  void setParameterOwner(final PsiElement o);
  PsiElement getParameterOwner();

  void setHighlightedParameter(final Object parameter);
  Object getHighlightedParameter();
  void setCurrentParameter(final int index);
  boolean isUIComponentEnabled(int index);
  void setUIComponentEnabled(int index, boolean enabled);

  int getParameterListStart();

  Object[] getObjectsToView();
  
  boolean isInnermostContext();
}
