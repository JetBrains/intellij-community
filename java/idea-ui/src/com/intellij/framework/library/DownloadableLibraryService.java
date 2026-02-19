// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.library;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import org.jetbrains.annotations.NotNull;

import java.net.URL;

public abstract class DownloadableLibraryService {
  public static DownloadableLibraryService getInstance() {
    return ApplicationManager.getApplication().getService(DownloadableLibraryService.class);
  }

  public abstract @NotNull DownloadableLibraryDescription createLibraryDescription(@NotNull String groupId, URL @NotNull ... localUrls);

  public abstract @NotNull CustomLibraryDescription createDescriptionForType(Class<? extends DownloadableLibraryType> typeClass);

  public abstract @NotNull LibraryPropertiesEditor createDownloadableLibraryEditor(@NotNull DownloadableLibraryDescription description,
                                                                                   @NotNull LibraryEditorComponent<LibraryVersionProperties> editorComponent,
                                                                                   @NotNull DownloadableLibraryType libraryType);
}
