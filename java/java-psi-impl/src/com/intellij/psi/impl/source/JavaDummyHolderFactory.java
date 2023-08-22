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
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class JavaDummyHolderFactory implements HolderFactory {
  @NotNull
  @Override
  public DummyHolder createHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context) {
    return new JavaDummyHolder(manager, contentElement, context);
  }

  @NotNull
  @Override
  public DummyHolder createHolder(@NotNull PsiManager manager,
                                  TreeElement contentElement, PsiElement context, CharTable table) {
    return new JavaDummyHolder(manager, contentElement, context, table);
  }

  @NotNull
  @Override
  public DummyHolder createHolder(@NotNull PsiManager manager, PsiElement context) {
    return new JavaDummyHolder(manager, context);
  }

  @NotNull
  @Override
  public DummyHolder createHolder(@NotNull PsiManager manager, Language language, PsiElement context) {
    return language == JavaLanguage.INSTANCE ? new JavaDummyHolder(manager, context) : new DummyHolder(manager, language, context);
  }

  @NotNull
  @Override
  public DummyHolder createHolder(@NotNull PsiManager manager, PsiElement context, CharTable table) {
    return new JavaDummyHolder(manager, context, table);
  }

  @NotNull
  @Override
  public DummyHolder createHolder(@NotNull PsiManager manager, CharTable table, Language language) {
    return new JavaDummyHolder(manager, table);
  }

  @NotNull
  @Override
  public DummyHolder createHolder(@NotNull PsiManager manager, CharTable table, boolean validity) {
    return new JavaDummyHolder(manager, table, validity);
  }
}