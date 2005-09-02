/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.pom.java.PomField;
import com.intellij.util.IncorrectOperationException;

public interface PsiField extends PsiMember, PsiVariable, PsiDocCommentOwner {
  PsiField[] EMPTY_ARRAY = new PsiField[0];
  PomField getPom();
  /**
   * Adds initializer to the field declaration.
   * Or, if initializer parameter is null, removes initializer from variable.
   */
  void setInitializer(PsiExpression initializer) throws IncorrectOperationException;
}
