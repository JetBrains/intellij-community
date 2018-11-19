// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class SearchScope {
  private static int hashCodeCounter;

  private transient int myHashCode;
  // to avoid System.identityHashCode() which was allegedly slow
  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  private final int myDefaultHashCode = ++hashCodeCounter;

  /**
   * Do not override this method because it would disable hash code caching.
   * To provide your own hash code please override {@link #calcHashCode()} instead.
   * @deprecated This is not the <s>droid</s> method that you should override, please override {@link #calcHashCode()} instead.
   */
  @Deprecated // to discourage overriding
  @Override
  public int hashCode() {
    int hashCode = myHashCode;
    if (hashCode == 0) {
      // benign race
      myHashCode = hashCode = calcHashCode();
    }
    return hashCode;
  }

  /**
   * To provide your own hash code please override this method instead of <s>{@link #hashCode()}</s> to be able to cache the computed hash code.
   */
  protected int calcHashCode() {
    return myDefaultHashCode;
  }

  @NotNull
  public String getDisplayName() {
    return PsiBundle.message("search.scope.unknown");
  }

  @Nullable
  public Icon getDisplayIcon() {
    return null;
  }

  @NotNull
  @Contract(pure = true)
  public abstract SearchScope intersectWith(@NotNull SearchScope scope2);

  @NotNull
  @Contract(pure = true)
  public abstract SearchScope union(@NotNull SearchScope scope);

  @Contract(pure = true)
  public abstract boolean contains(@NotNull VirtualFile file);
}
