// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.frameworkSupport;

import com.intellij.framework.library.DownloadableLibraryType;
import com.intellij.openapi.roots.libraries.LibraryKind;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public class CustomLibraryDescriptionImpl extends CustomLibraryDescriptionBase {
  private final DownloadableLibraryType myLibraryType;

  public CustomLibraryDescriptionImpl(@NotNull DownloadableLibraryType downloadableLibraryType) {
    super(downloadableLibraryType.getLibraryCategoryName());
    myLibraryType = downloadableLibraryType;
  }

  @Override
  public @NotNull Set<? extends LibraryKind> getSuitableLibraryKinds() {
    return Collections.singleton(myLibraryType.getKind());
  }

  @Override
  public DownloadableLibraryType getDownloadableLibraryType() {
    return myLibraryType;
  }

  @Override
  public String toString() {
    return "CustomLibraryDescriptionImpl(" + myLibraryType.getKind().getKindId() + ")";
  }
}
