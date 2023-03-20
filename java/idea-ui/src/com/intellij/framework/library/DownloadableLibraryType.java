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

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.*;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.URL;
import java.util.List;
import java.util.function.Supplier;

public abstract class DownloadableLibraryType extends LibraryType<LibraryVersionProperties> {
  private final Supplier<@Nls(capitalization = Nls.Capitalization.Title) String> myLibraryCategoryName;
  private final DownloadableLibraryDescription myLibraryDescription;

  /**
   * @deprecated The constructor is meant to maintain the binary compatibility with external plugins.
   * Please use the constructors with a messagePointer for {@link DownloadableLibraryType#myLibraryCategoryName}
   */
  @Deprecated(forRemoval = true)
  protected DownloadableLibraryType(@NotNull @Nls(capitalization = Nls.Capitalization.Title) String libraryCategoryName,
                                    @NotNull String libraryTypeId,
                                    @NotNull String groupId,
                                    URL @NotNull ... localUrls) {
    this(() -> libraryCategoryName, libraryTypeId, groupId, localUrls);
  }

  /**
   * Creates instance of library type. You also <strong>must</strong> override {@link #getLibraryTypeIcon()} method and return non-null value
   * from it.
   *
   * @param libraryCategoryName presentable description of the library type
   * @param libraryTypeId       unique id of the library type, used for serialization
   * @param groupId             name of directory on https://frameworks.jetbrains.com site which contains information about available library versions
   * @param localUrls           URLs of xml files containing information about the library versions (see /contrib/osmorc/src/org/osmorc/facet/osgi.core.xml for example)
   */
  protected DownloadableLibraryType(@NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Title) String> libraryCategoryName,
                                    @NotNull String libraryTypeId,
                                    @NotNull String groupId,
                                    URL @NotNull ... localUrls) {
    super(new PersistentLibraryKind<>(libraryTypeId) {
      @NotNull
      @Override
      public LibraryVersionProperties createDefaultProperties() {
        return new LibraryVersionProperties();
      }
    });
    myLibraryCategoryName = libraryCategoryName;
    myLibraryDescription = DownloadableLibraryService.getInstance().createLibraryDescription(groupId, localUrls);
  }

  @Nullable
  private static LibraryVersionProperties detectVersion(List<? extends VirtualFile> classesRoots, String detectionClass) {
    if (!LibraryUtil.isClassAvailableInLibrary(classesRoots, detectionClass)) {
      return null;
    }
    final String version = JarVersionDetectionUtil.detectJarVersion(detectionClass, classesRoots);
    return new LibraryVersionProperties(version);
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
    return myLibraryCategoryName.get();
  }

  @Override
  public String getDescription(@NotNull LibraryVersionProperties properties) {
    final String versionString = properties.getVersionString();
    final int versionStringPresent = versionString != null ? 0 : 1;
    return JavaUiBundle.message("downloadable.library.type.description", getLibraryCategoryName(), versionString, versionStringPresent);
  }

  @Override
  public LibraryPropertiesEditor createPropertiesEditor(@NotNull LibraryEditorComponent<LibraryVersionProperties> editorComponent) {
    return DownloadableLibraryService.getInstance().createDownloadableLibraryEditor(myLibraryDescription, editorComponent, this);
  }

  @NotNull
  public abstract Icon getLibraryTypeIcon();

  @Override
  @NotNull
  public Icon getIcon(LibraryVersionProperties properties) {
    return getLibraryTypeIcon();
  }

  protected abstract String @NotNull [] getDetectionClassNames();

  @Override
  public LibraryVersionProperties detect(@NotNull List<VirtualFile> classesRoots) {
    for (String className : getDetectionClassNames()) {
      final LibraryVersionProperties versionProperties = detectVersion(classesRoots, className);
      if (versionProperties != null) return versionProperties;
    }
    return null;
  }
}
