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

package com.intellij.psi.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * An extension called before notifying {@link com.intellij.psi.PsiTreeChangeListener}s of events.<p></p>
 *
 * Try to avoid processing PSI events at all cost! See {@link com.intellij.psi.PsiTreeChangeEvent} documentation for more details.
 *
 * @author yole
 */
public interface PsiTreeChangePreprocessor {
  ExtensionPointName<PsiTreeChangePreprocessor> EP_NAME = ExtensionPointName.create("com.intellij.psi.treeChangePreprocessor");

  void treeChanged(@NotNull PsiTreeChangeEventImpl event);
}
