/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public interface ExpectedTypeInfo {
  int TYPE_STRICTLY = 0;
  int TYPE_OR_SUBTYPE = 1;
  int TYPE_OR_SUPERTYPE = 2;
  int TYPE_BETWEEN = 3;
  int TYPE_SAME_SHAPED = 4;

  @MagicConstant(valuesFromClass = ExpectedTypeInfo.class)
  @interface Type {}

  ExpectedTypeInfo[] EMPTY_ARRAY = new ExpectedTypeInfo[0];

  PsiMethod getCalledMethod();

  @NotNull
  PsiType getType();

  PsiType getDefaultType();

  @Type
  int getKind();

  boolean equals(ExpectedTypeInfo info);

  String toString();

  @NotNull
  ExpectedTypeInfo[] intersect(@NotNull ExpectedTypeInfo info);

  @NotNull
  TailType getTailType();
}
