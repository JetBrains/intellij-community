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

package com.intellij.openapi.editor;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement the interface and register the implementation as {@code hectorComponentProvider} extension to provide additional UI in
 * "Code inspection highlighting settings" popup which is shown after clicking on "Hector" icon in the status bar.
 */
public interface HectorComponentPanelsProvider {
  ExtensionPointName<HectorComponentPanelsProvider> EP_NAME = ExtensionPointName.create("com.intellij.hectorComponentProvider");

  @Nullable HectorComponentPanel createConfigurable(@NotNull PsiFile file);
}
