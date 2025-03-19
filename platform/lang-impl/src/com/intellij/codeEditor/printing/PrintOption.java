// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeEditor.printing;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class PrintOption {
  public static final ExtensionPointName<PrintOption> EP_NAME = ExtensionPointName.create("com.intellij.printOption");
  
  public abstract @Nullable Map<Integer, PsiReference> collectReferences(@NotNull PsiFile psiFile, @NotNull Map<PsiFile, PsiFile> filesMap);

  public abstract @NotNull UnnamedConfigurable createConfigurable();
}