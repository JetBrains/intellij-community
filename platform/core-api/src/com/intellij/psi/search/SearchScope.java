// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public abstract class SearchScope {
  private static int hashCodeCounter;

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  private final int myHashCode = hashCodeCounter++;

  /**
   * Overridden for performance reason. Object.hashCode() is native method and becomes a bottleneck when called often.
   *
   * @return hashCode value semantically identical to one from Object but not native
   */
  @Override
  public int hashCode() {
    return myHashCode;
  }

  @NotNull
  public String getDisplayName() {
    return PsiBundle.message("search.scope.unknown");
  }

  @Nullable
  public Icon getDisplayIcon() {
    return null;
  }

  @NotNull public abstract SearchScope intersectWith(@NotNull SearchScope scope2);
  @NotNull public abstract SearchScope union(@NotNull SearchScope scope);

  public abstract boolean contains(@NotNull VirtualFile file);
}
