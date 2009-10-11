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
package com.intellij.refactoring;

import com.intellij.psi.PsiType;

/**
 * @author dsl
 */
public interface IntroduceParameterRefactoring extends Refactoring {
  int REPLACE_FIELDS_WITH_GETTERS_NONE = 0;
  int REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE = 1;
  int REPLACE_FIELDS_WITH_GETTERS_ALL = 2;

  void enforceParameterType(PsiType forcedType);
  void setFieldReplacementPolicy(int policy);

  PsiType getForcedType();
  int getFieldReplacementPolicy();
}
