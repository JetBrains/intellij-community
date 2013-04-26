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
package com.intellij.psi;

import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a Java class initializer block.
 */
public interface PsiClassInitializer extends PsiMember {
  /**
   * The empty array of PSI class initializers which can be reused to avoid unnecessary allocations.
   */
  PsiClassInitializer[] EMPTY_ARRAY = new PsiClassInitializer[0];

  ArrayFactory<PsiClassInitializer> ARRAY_FACTORY = new ArrayFactory<PsiClassInitializer>() {
    @NotNull
    @Override
    public PsiClassInitializer[] create(final int count) {
      return count == 0 ? EMPTY_ARRAY : new PsiClassInitializer[count];
    }
  };

  /**
   * Returns the contents of the class initializer block.
   *
   * @return the code block representing the contents of the class initializer block.
   */
  @NotNull
  PsiCodeBlock getBody();
}
