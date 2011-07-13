/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.framework.library;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import org.jetbrains.annotations.NotNull;

import java.net.URL;

/**
 * @author nik
 */
public abstract class DownloadableLibraryService {
  public static DownloadableLibraryService getInstance() {
    return ServiceManager.getService(DownloadableLibraryService.class);
  }

  @NotNull
  public abstract DownloadableLibraryDescription createLibraryDescription(@NotNull String groupId, @NotNull URL... localUrls);

  @NotNull
  public abstract CustomLibraryDescription createDescriptionForType(Class<? extends DownloadableLibraryType> typeClass);

  @NotNull
  public abstract LibraryPropertiesEditor createDownloadableLibraryEditor(@NotNull DownloadableLibraryDescription description,
                                   @NotNull LibraryEditorComponent<LibraryVersionProperties> editorComponent,
                                   @NotNull DownloadableLibraryType libraryType);
}
