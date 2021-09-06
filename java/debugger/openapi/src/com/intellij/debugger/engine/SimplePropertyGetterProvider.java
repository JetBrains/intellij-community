// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to support simple getter filtering during stepping for non-java languages
 */
public interface SimplePropertyGetterProvider {
  ExtensionPointName<SimplePropertyGetterProvider> EP_NAME = ExtensionPointName.create("com.intellij.debugger.simplePropertyGetterProvider");

  boolean isInsideSimpleGetter(@NotNull PsiElement element);
}
