/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows SmartPointer that points to stubbed psi element to survive stub-to-AST switch
 *
 * @author Dennis.Ushakov
 */
public abstract class SmartPointerAnchorProvider {
  public static final ExtensionPointName<SmartPointerAnchorProvider> EP_NAME = ExtensionPointName.create("com.intellij.smartPointer.anchorProvider");

  /**
   * Provides anchor used for restoring elements after stub-to-AST switch.
   * One can use name identifier (such as tag or method name) as an anchor
   * @param element
   * @return anchor to be used when restoring element
   */
  @Nullable
  public abstract PsiElement getAnchor(@NotNull PsiElement element);

  /**
   * @param anchor
   * @return restored original element using anchor
   */
  @Nullable
  public abstract PsiElement restoreElement(@NotNull PsiElement anchor);
}
