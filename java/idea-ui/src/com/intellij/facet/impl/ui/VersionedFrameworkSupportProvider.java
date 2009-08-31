/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportConfigurable;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportModel;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class VersionedFrameworkSupportProvider extends FrameworkSupportProvider {

  protected VersionedFrameworkSupportProvider(final @NonNls @NotNull String id, final @NotNull String title) {
    super(id, title);
  }

  protected abstract void addSupport(Module module, ModifiableRootModel rootModel, String version, final @Nullable Library library);

  @NotNull
  public String[] getVersions() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Nullable
  public String getDefaultVersion() {
    return null;
  }

  @NotNull
  public VersionConfigurable createConfigurable(final @NotNull FrameworkSupportModel model) {
    return new VersionConfigurable(this, getVersions(), getDefaultVersion());
  }

  @NotNull
  protected LibraryInfo[] getLibraries(final String selectedVersion) {
    return LibraryInfo.EMPTY_ARRAY;
  }

  @NotNull @NonNls
  protected String getLibraryName(final String selectedVersion) {
    return FrameworkSupportConfigurable.DEFAULT_LIBRARY_NAME;
  }

}
