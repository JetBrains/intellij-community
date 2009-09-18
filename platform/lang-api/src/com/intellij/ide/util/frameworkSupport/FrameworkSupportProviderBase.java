/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.util.frameworkSupport;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class FrameworkSupportProviderBase extends FrameworkSupportProvider {

  protected FrameworkSupportProviderBase(final @NonNls @NotNull String id, final @NotNull String title) {
    super(id, title);
  }

  protected abstract void addSupport(@NotNull Module module, @NotNull ModifiableRootModel rootModel, FrameworkVersion version, final @Nullable Library library);

  @NotNull
  public List<FrameworkVersion> getVersions() {
    return Collections.emptyList();
  }

  public String getVersionLabelText() {
    return "Version:";
  }

  @NotNull
  public FrameworkSupportConfigurableBase createConfigurable(final @NotNull FrameworkSupportModel model) {
    return new FrameworkSupportConfigurableBase(this, getVersions(), getVersionLabelText());
  }
}
