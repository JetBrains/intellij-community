// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.addSupport;

import com.intellij.framework.library.FrameworkLibraryVersionFilter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class FrameworkSupportInModuleConfigurable implements Disposable {
  public abstract @Nullable JComponent createComponent();

  public abstract void addSupport(@NotNull Module module, @NotNull ModifiableRootModel rootModel,
                                  @NotNull ModifiableModelsProvider modifiableModelsProvider);

  public @Nullable CustomLibraryDescription createLibraryDescription() {
    return null;
  }

  public @NotNull FrameworkLibraryVersionFilter getLibraryVersionFilter() {
    return FrameworkLibraryVersionFilter.ALL;
  }

  public void onFrameworkSelectionChanged(boolean selected) {
  }

  public boolean isOnlyLibraryAdded() {
    return false;
  }

  public boolean isVisible() {
    return true;
  }

  @Override
  public void dispose() {
  }
}
