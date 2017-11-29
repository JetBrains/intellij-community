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
package com.intellij.slicer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaReference;
import org.jetbrains.annotations.NotNull;

public class JavaSlicerAnalysisUtil {
  public static final SliceLeafEquality LEAF_ELEMENT_EQUALITY = new SliceLeafEquality() {
    @NotNull
    @Override
    protected PsiElement substituteElement(@NotNull PsiElement element) {
      if (element instanceof PsiJavaReference) {
        PsiElement resolved = ((PsiJavaReference)element).resolve();
        if (resolved != null) return resolved;
      }
      return element;
    }
  };

  @NotNull
  public static SliceLeafAnalyzer createLeafAnalyzer() {
    return new SliceLeafAnalyzer(LEAF_ELEMENT_EQUALITY, JavaSliceProvider.getInstance());
  }

  private JavaSlicerAnalysisUtil() {
  }
}
