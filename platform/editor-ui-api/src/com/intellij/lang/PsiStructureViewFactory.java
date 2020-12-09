// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Should be registered as language extension
 * @see LanguageStructureViewBuilder
 */
@FunctionalInterface
public interface PsiStructureViewFactory {
  ExtensionPointName<KeyedLazyInstance<PsiStructureViewFactory>> EP_NAME = ExtensionPointName.create("com.intellij.lang.psiStructureViewFactory");

  @Nullable
  StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile psiFile);
}