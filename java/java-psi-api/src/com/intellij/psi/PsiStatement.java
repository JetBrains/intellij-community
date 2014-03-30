/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a Java statement.
 */
public interface PsiStatement extends PsiElement {
  /**
   * The empty array of PSI statements which can be reused to avoid unnecessary allocations.
   */
  PsiStatement[] EMPTY_ARRAY = new PsiStatement[0];

  ArrayFactory<PsiStatement> ARRAY_FACTORY = new ArrayFactory<PsiStatement>() {
    @NotNull
    @Override
    public PsiStatement[] create(final int count) {
      return count == 0 ? PsiStatement.EMPTY_ARRAY : new PsiStatement[count];
    }
  };
}
