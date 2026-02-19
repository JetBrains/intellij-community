// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides a JSON schema depending on the contents of the file this schema is requested for.
 */
public interface ContentAwareJsonSchemaFileProvider {
  ExtensionPointName<ContentAwareJsonSchemaFileProvider> EP_NAME =
    ExtensionPointName.create("JavaScript.JsonSchema.ContentAwareSchemaFileProvider");

  @Nullable
  VirtualFile getSchemaFile(@NotNull PsiFile psiFile);
}
