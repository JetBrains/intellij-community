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
package com.intellij.ide.util.frameworkSupport;

import com.intellij.facet.impl.ui.libraries.RequiredLibrariesInfo;
import com.intellij.facet.ui.libraries.LibraryDownloadInfo;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.framework.library.DownloadableLibraryDescription;
import com.intellij.framework.library.DownloadableLibraryFileDescription;
import com.intellij.framework.FrameworkAvailabilityCondition;
import com.intellij.framework.library.FrameworkLibraryVersion;
import com.intellij.framework.library.impl.DownloadableLibraryDescriptionImpl;
import com.intellij.framework.library.impl.DownloadableLibraryFileDescriptionImpl;
import com.intellij.framework.library.impl.FrameworkLibraryVersionImpl;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class OldCustomLibraryDescription extends CustomLibraryDescriptionBase {
  private final DownloadableLibraryDescription myDownloadableDescription;
  private final List<FrameworkVersion> myVersions;

  public OldCustomLibraryDescription(@NotNull LibraryInfo[] libraryInfos, @NotNull String defaultLibraryName) {
    this(Collections.singletonList(new FrameworkVersion(defaultLibraryName, defaultLibraryName, libraryInfos, true)), defaultLibraryName);
  }

  private OldCustomLibraryDescription(final List<FrameworkVersion> versions, String defaultLibraryName) {
    super(defaultLibraryName);
    myVersions = versions;
    final List<FrameworkLibraryVersion> libraryVersions = new ArrayList<>();
    for (FrameworkVersion version : versions) {
      List<DownloadableLibraryFileDescription> downloads = new ArrayList<>();
      for (LibraryInfo info : version.getLibraries()) {
        final LibraryDownloadInfo downloadingInfo = info.getDownloadingInfo();
        if (downloadingInfo != null) {
          downloads.add(new DownloadableLibraryFileDescriptionImpl(downloadingInfo.getDownloadUrl(), downloadingInfo.getFileNamePrefix(),
                                                                   downloadingInfo.getFileNameSuffix(), null, null, false));
        }
      }
      String libraryName = version.getLibraryName();
      libraryVersions.add(new FrameworkLibraryVersionImpl(libraryName, version.getVersionName(), FrameworkAvailabilityCondition.ALWAYS_TRUE, downloads,
                                                          libraryName));
    }
    myDownloadableDescription = !libraryVersions.isEmpty() ? new DownloadableLibraryDescriptionImpl(libraryVersions) : null;
  }

  public DownloadableLibraryDescription getDownloadableDescription() {
    return myDownloadableDescription;
  }

  public boolean isSuitableLibrary(@NotNull Library library, @NotNull LibrariesContainer container) {
    for (FrameworkVersion version : myVersions) {
      RequiredLibrariesInfo info = new RequiredLibrariesInfo(version.getLibraries());
      if (info.checkLibraries(container.getLibraryFiles(library, OrderRootType.CLASSES)) == null) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public Set<? extends LibraryKind> getSuitableLibraryKinds() {
    return Collections.emptySet();
  }

  @Nullable
  public static CustomLibraryDescription createByVersions(List<? extends FrameworkVersion> versions) {
    String defaultLibraryName = null;
    List<FrameworkVersion> withLibraries = new ArrayList<>();
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
