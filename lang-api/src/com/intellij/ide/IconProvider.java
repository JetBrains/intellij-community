/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public interface IconProvider {
  //ExtensionPointName<IconProvider> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.iconProvider");

  /**
   * @param element for which icon is shown
   * @param flags used for customizing the icon appearance. Flags are listed in {@link com.intellij.openapi.util.Iconable}
   * @see com.intellij.openapi.util.Iconable
   */
  @Nullable
  Icon getIcon(@NotNull PsiElement element, int flags);
}
