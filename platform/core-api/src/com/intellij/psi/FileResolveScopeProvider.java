// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi;

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Implemented by PSI files which must have non-standard resolve scope for elements contained in them.
 */
public interface FileResolveScopeProvider {
  @NotNull
  GlobalSearchScope getFileResolveScope();
  boolean ignoreReferencedElementAccessibility();
}
