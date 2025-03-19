// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.library.impl;

import com.intellij.facet.frameworks.beans.Artifact;
import com.intellij.facet.frameworks.beans.RequiredFrameworkVersion;
import com.intellij.framework.FrameworkAvailabilityCondition;
import com.intellij.framework.FrameworkVersion;
import com.intellij.framework.library.DownloadableLibraryDescription;
import com.intellij.framework.library.DownloadableLibraryService;
import com.intellij.framework.library.DownloadableLibraryType;
import com.intellij.framework.library.LibraryVersionProperties;
import com.intellij.ide.util.frameworkSupport.CustomLibraryDescriptionImpl;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import org.jetbrains.annotations.NotNull;

import java.net.URL;

public final class DownloadableLibraryServiceImpl extends DownloadableLibraryService {
  private static final Logger LOG = Logger.getInstance(DownloadableLibraryServiceImpl.class);

  @Override
  public @NotNull DownloadableLibraryDescription createLibraryDescription(@NotNull String groupId, final URL @NotNull ... localUrls) {
    return new LibraryVersionsFetcher(groupId, localUrls) {
      @Override
      protected @NotNull FrameworkAvailabilityCondition createAvailabilityCondition(Artifact version) {
        RequiredFrameworkVersion groupVersion = version.getRequiredFrameworkVersion();
        if (groupVersion != null) {
          return new FrameworkLibraryAvailabilityCondition(groupVersion.myGroupId, groupVersion.myVersion);
        }
        return FrameworkAvailabilityCondition.ALWAYS_TRUE;
      }
    };
  }

  @Override
  public @NotNull CustomLibraryDescription createDescriptionForType(Class<? extends DownloadableLibraryType> typeClass) {
    final DownloadableLibraryType libraryType = LibraryType.EP_NAME.findExtension(typeClass);
    LOG.assertTrue(libraryType != null, typeClass);
    return new CustomLibraryDescriptionImpl(libraryType);
  }

  @Override
  public @NotNull LibraryPropertiesEditor createDownloadableLibraryEditor(@NotNull DownloadableLibraryDescription description,
                                                                          @NotNull LibraryEditorComponent<LibraryVersionProperties> editorComponent,
                                                                          @NotNull DownloadableLibraryType libraryType) {
    return new DownloadableLibraryPropertiesEditor(description, editorComponent, libraryType);
  }

  private static class FrameworkLibraryAvailabilityCondition extends FrameworkAvailabilityCondition {
    private final String myGroupId;
    private final String myVersionId;

    FrameworkLibraryAvailabilityCondition(String groupId, String versionId) {
      myGroupId = groupId;
      myVersionId = versionId;
    }

    @Override
    public boolean isAvailableFor(@NotNull FrameworkSupportModel model) {
      FrameworkVersion selectedVersion = ((FrameworkSupportModelBase)model).getSelectedVersion(myGroupId);
      return selectedVersion != null && myVersionId.equals(selectedVersion.getId());
    }
  }
}
