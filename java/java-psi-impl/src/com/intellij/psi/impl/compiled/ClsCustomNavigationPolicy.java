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
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** @deprecated API no longer supported, use {@link com.intellij.psi.compiled.ClassFileDecompilers} instead (to remove in IDEA 14) */
@Deprecated
@SuppressWarnings({"UnusedDeclaration"})
public interface ClsCustomNavigationPolicy {
  ExtensionPointName<ClsCustomNavigationPolicy> EP_NAME = ExtensionPointName.create("com.intellij.psi.clsCustomNavigationPolicy");

  @Nullable
  PsiElement getNavigationElement(@NotNull ClsClassImpl clsClass);

  @Nullable
  PsiElement getNavigationElement(@NotNull ClsMethodImpl clsMethod);

  @Nullable
  PsiElement getNavigationElement(@NotNull ClsFieldImpl clsField);
}
