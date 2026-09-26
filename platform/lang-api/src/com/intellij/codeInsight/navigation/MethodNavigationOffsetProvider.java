// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.navigation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;


public interface MethodNavigationOffsetProvider {
  ExtensionPointName<MethodNavigationOffsetProvider> EP_NAME = ExtensionPointName.create("com.intellij.methodNavigationOffsetProvider");

  int @Nullable [] getMethodNavigationOffsets(PsiFile file, int caretOffset);
}
