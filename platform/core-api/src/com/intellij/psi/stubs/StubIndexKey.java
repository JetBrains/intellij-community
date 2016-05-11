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

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.util.indexing.ID;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class StubIndexKey<K, Psi extends PsiElement> extends ID<K, Psi> {
  private StubIndexKey(@NonNls String name) {
    super(name);
  }

  @NotNull
  public static synchronized <K, Psi extends PsiElement> StubIndexKey<K, Psi> createIndexKey(@NonNls @NotNull String name) {
    return new StubIndexKey<K, Psi>(name);
  }

}