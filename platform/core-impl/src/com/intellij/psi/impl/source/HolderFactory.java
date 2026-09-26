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

/*
 * @author max
 */
package com.intellij.psi.impl.source;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface HolderFactory {
  @NotNull
  DummyHolder createHolder(@NotNull PsiManager manager, @NotNull TreeElement contentElement, @Nullable PsiElement context);
  @NotNull
  DummyHolder createHolder(@NotNull PsiManager manager, @Nullable CharTable table, boolean validity);
  @NotNull
  DummyHolder createHolder(@NotNull PsiManager manager, @Nullable PsiElement context);
  @NotNull
  DummyHolder createHolder(@NotNull PsiManager manager, @NotNull Language language, @Nullable PsiElement context);
  @NotNull
  DummyHolder createHolder(@NotNull PsiManager manager, @Nullable TreeElement contentElement, @Nullable PsiElement context, @Nullable CharTable table);
  @NotNull
  DummyHolder createHolder(@NotNull PsiManager manager, @Nullable PsiElement context, @Nullable CharTable table);
  @NotNull
  DummyHolder createHolder(@NotNull PsiManager manager, @Nullable CharTable table, @NotNull Language language);
  
}