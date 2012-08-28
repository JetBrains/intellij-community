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

import com.intellij.openapi.roots.libraries.JarVersionDetectionUtil;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.URL;
import java.util.List;

public abstract class DownloadableLibraryTypeBase extends DownloadableLibraryType {
  private final Icon myIcon;

  protected DownloadableLibraryTypeBase(@NotNull String libraryCategoryName,
                                        @NotNull String libraryTypeId,
                                        @NotNull String groupId,
                                        @NotNull Icon icon,
                                        @NotNull URL... localUrls) {
    super(new PersistentLibraryKind<LibraryVersionProperties>(libraryTypeId) {
      @NotNull
      @Override
      public LibraryVersionProperties createDefaultProperties() {
        return new LibraryVersionProperties();
      }
    }, libraryCategoryName,
          DownloadableLibraryService.getInstance().createLibraryDescription(groupId, localUrls));
    myIcon = icon;
  }

  public Icon getIcon() {
    return myIcon;
  }

  protected abstract String[] getDetectionClassNames();

  @Override
  public LibraryVersionProperties detect(@NotNull List<VirtualFile> classesRoots) {
    for (String className : getDetectionClassNames()) {

      final LibraryVersionProperties versionProperties = detectVersion(classesRoots, className);
      if (versionProperties != null) return versionProperties;
    }
    return null;
  }

  @Nullable
  private static LibraryVersionProperties detectVersion(List<VirtualFile> classesRoots, String detectionClass) {
    if (!LibraryUtil.isClassAvailableInLibrary(classesRoots, detectionClass)) {
      return null;
    }
    final String version = JarVersionDetectionUtil.detectJarVersion(detectionClass, classesRoots);
    return new LibraryVersionProperties(version);
  }
}
