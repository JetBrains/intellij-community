/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.slicer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public class JavaSliceUsage extends SliceUsage {
  @NotNull private final PsiSubstitutor mySubstitutor;

  /**
   * 0 means bare expression 'x', 1 means x[?], 2 means x[?][?] etc
   */
  private final int myIndexNesting;

  /**
   * "" means no field, otherwise it's a name of fake field of container, e.g. "keys" for Map
   */
  @NotNull private final String mySyntheticField;

  public JavaSliceUsage(@NotNull PsiElement element, @Nullable SliceUsage parent, @NotNull SliceAnalysisParams params,
                        @NotNull PsiSubstitutor substitutor, int indexNesting, @NotNull String syntheticField) {
    super(element, parent, params);
    mySubstitutor = substitutor;
    myIndexNesting = indexNesting;
    mySyntheticField = syntheticField;
  }

  @NotNull
  @Override
  SliceUsage copy() {
    return new JavaSliceUsage(getElement(), getParent(), params, mySubstitutor, myIndexNesting, mySyntheticField);
  }

  @NotNull
  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

  public int getIndexNesting() {
    return myIndexNesting;
  }

  @NotNull
  public String getSyntheticField() {
    return mySyntheticField;
  }
}
