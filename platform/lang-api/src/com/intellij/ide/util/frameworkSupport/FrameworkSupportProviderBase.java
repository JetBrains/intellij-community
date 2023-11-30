// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util.frameworkSupport;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class FrameworkSupportProviderBase extends FrameworkSupportProvider {

  protected FrameworkSupportProviderBase(final @NonNls @NotNull String id, final @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    super(id, title);
  }

  protected abstract void addSupport(@NotNull Module module, @NotNull ModifiableRootModel rootModel, FrameworkVersion version, final @Nullable Library library);

  public @NotNull List<FrameworkVersion> getVersions() {
    return Collections.emptyList();
  }

  public @NlsContexts.Label String getVersionLabelText() {
    return LangBundle.message("label.framework.version");
  }

  @Override
  public @NotNull FrameworkSupportConfigurable createConfigurable(final @NotNull FrameworkSupportModel model) {
    return new FrameworkSupportConfigurableBase(this, model, getVersions(), getVersionLabelText());
  }
}
