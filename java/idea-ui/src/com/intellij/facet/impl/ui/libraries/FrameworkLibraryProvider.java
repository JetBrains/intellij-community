// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl.ui.libraries;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryKind;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public abstract class FrameworkLibraryProvider {
  public abstract @NotNull String getPresentableName();

  public abstract Set<LibraryKind> getAvailableLibraryKinds();

  public abstract @NotNull Library createLibrary(@NotNull Set<? extends LibraryKind> suitableLibraryKinds);
}
