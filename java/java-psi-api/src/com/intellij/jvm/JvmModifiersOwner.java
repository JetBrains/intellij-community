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
package com.intellij.jvm;

import com.intellij.psi.PsiModifier.ModifierConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public interface JvmModifiersOwner extends JvmElement {

  @SuppressWarnings("TypeParameterExtendsFinalClass")
  @NotNull
  default Collection<? extends String> getModifiers() {
    return Collections.emptyList();
  }

  default boolean hasModifier(@ModifierConstant @NotNull @NonNls String modifier) {
    return getModifiers().contains(modifier);
  }
}
