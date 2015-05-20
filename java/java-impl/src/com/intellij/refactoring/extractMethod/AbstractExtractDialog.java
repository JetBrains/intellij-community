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

/*
 * User: anna
 * Date: 15-May-2008
 */
package com.intellij.refactoring.extractMethod;

import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.util.VariableData;

public interface AbstractExtractDialog {

  String getChosenMethodName();
  VariableData[] getChosenParameters();
  @PsiModifier.ModifierConstant
  String getVisibility();
  boolean isMakeStatic();
  boolean isChainedConstructor();
  PsiType getReturnType();

  void show();
  boolean isOK();
}