/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.util.frameworkSupport;

import com.intellij.facet.impl.ui.libraries.RequiredLibrariesInfo;
import com.intellij.facet.ui.libraries.LibraryDownloadInfo;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.framework.library.DownloadableFileDescription;
import com.intellij.framework.library.DownloadableLibraryDescription;
import com.intellij.framework.library.FrameworkLibraryVersion;
import com.intellij.framework.library.impl.DownloadableFileDescriptionImpl;
import com.intellij.framework.library.impl.DownloadableLibraryDescriptionImpl;
import com.intellij.framework.library.impl.FrameworkLibraryVersionImpl;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryFilter;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class OldCustomLibraryDescription extends CustomLibraryDescriptionBase {
  private final LibraryFilter mySuitableLibraryCondition;
  private final DownloadableLibraryDescription myDownloadableDescription;

  public OldCustomLibraryDescription(@NotNull LibraryInfo[] libraryInfos, @NotNull String defaultLibraryName) {
    this(Collections.singletonList(new FrameworkVersion(defaultLibraryName, defaultLibraryName, libraryInfos, true)), defaultLibraryName);
  }

  public OldCustomLibraryDescription(final List<FrameworkVersion> versions, String defaultLibraryName) {
    super(defaultLibraryName);
    final List<FrameworkLibraryVersion> libraryVersions = new ArrayList<FrameworkLibraryVersion>();
    for (FrameworkVersion version : versions) {
      List<DownloadableFileDescription> downloads = new ArrayList<DownloadableFileDescription>();
      for (LibraryInfo info : version.getLibraries()) {
        final LibraryDownloadInfo downloadingInfo = info.getDownloadingInfo();
        if (downloadingInfo != null) {
          final DownloadableFileDescription element = new DownloadableFileDescriptionImpl(downloadingInfo.getDownloadUrl(), downloadingInfo.getFileNamePrefix(), downloadingInfo.getFileNameSuffix());
          downloads.add(element);
        }
      }
      libraryVersions.add(new FrameworkLibraryVersionImpl(version.getVersionName(), downloads, version.getLibraryName(), version));
    }
    myDownloadableDescription = !libraryVersions.isEmpty() ? new DownloadableLibraryDescriptionImpl(libraryVersions) : null;
    mySuitableLibraryCondition = new LibraryFilter() {
      @Override
      public boolean isSuitableLibrary(@NotNull List<VirtualFile> classesRoots,
                                       @Nullable LibraryType<?> type) {
        for (FrameworkVersion version : versions) {
          RequiredLibrariesInfo info = new RequiredLibrariesInfo(version.getLibraries());
          if (info.checkLibraries(classesRoots) == null) {
            return true;
          }
        }
        return false;
      }
    };
  }

  @Override
  public DownloadableLibraryDescription getDownloadableDescription() {
    return myDownloadableDescription;
  }

  @NotNull
  @Override
  public LibraryFilter getSuitableLibraryFilter() {
    return mySuitableLibraryCondition;
  }

  public static CustomLibraryDescription createByVersions(List<FrameworkVersion> versions) {
    String defaultLibraryName = null;
    List<FrameworkVersion> withLibraries = new ArrayList<FrameworkVersion>();
    for (FrameworkVersion version : versions) {
      if (version.getLibraries().length > 0) {
        if (version.isDefault()) {
          defaultLibraryName = version.getLibraryName();
        }
        withLibraries.add(version);
      }
    }
    if (withLibraries.isEmpty()) return null;


    if (defaultLibraryName == null) {
      defaultLibraryName = withLibraries.get(0).getLibraryName();
    }

    return new OldCustomLibraryDescription(withLibraries, defaultLibraryName);
  }
}
