// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  public abstract DownloadableLibraryDescription createLibraryDescription(@NotNull String groupId, URL @NotNull ... localUrls);

  @NotNull
  public abstract CustomLibraryDescription createDescriptionForType(Class<? extends DownloadableLibraryType> typeClass);

  @NotNull
  public abstract LibraryPropertiesEditor createDownloadableLibraryEditor(@NotNull DownloadableLibraryDescription description,
                                   @NotNull LibraryEditorComponent<LibraryVersionProperties> editorComponent,
                                   @NotNull DownloadableLibraryType libraryType);
}
