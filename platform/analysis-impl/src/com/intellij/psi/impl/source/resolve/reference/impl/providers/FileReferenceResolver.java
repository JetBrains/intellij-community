package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FileReferenceResolver {
  @Nullable
  PsiFileSystemItem resolveFileReference(@NotNull FileReference reference, @NotNull String name);
}