/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class DownloadableLibraryType extends LibraryType<LibraryVersionProperties> {
  private final String myLibraryCategoryName;
  private final DownloadableLibraryDescription myLibraryDescription;

  public DownloadableLibraryType(@NotNull PersistentLibraryKind<LibraryVersionProperties> kind, @NotNull String libraryCategoryName,
                                 @NotNull DownloadableLibraryDescription description) {
    super(kind);
    myLibraryCategoryName = libraryCategoryName;
    myLibraryDescription = description;
  }

  @Override
  public String getCreateActionName() {
    return null;
  }

  @Override
  public NewLibraryConfiguration createNewLibrary(@NotNull JComponent parentComponent,
                                                  @Nullable VirtualFile contextDirectory,
                                                  @NotNull Project project) {
    return null;
  }

  @NotNull
  public DownloadableLibraryDescription getLibraryDescription() {
    return myLibraryDescription;
  }

  public String getLibraryCategoryName() {
    return myLibraryCategoryName;
  }

  @Override
  public String getDescription(@NotNull LibraryVersionProperties properties) {
    final String versionString = properties.getVersionString();
    return StringUtil.capitalize(myLibraryCategoryName) + " library" + (versionString != null ? " of version " + versionString : "");
  }

  @Override
  public LibraryPropertiesEditor createPropertiesEditor(@NotNull LibraryEditorComponent<LibraryVersionProperties> editorComponent) {
    return DownloadableLibraryService.getInstance().createDownloadableLibraryEditor(myLibraryDescription, editorComponent, this);
  }

  @Override
  public Icon getIcon() {
    return null;
  }
}
