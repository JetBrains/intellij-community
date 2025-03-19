// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util.frameworkSupport;

import com.intellij.framework.library.FrameworkLibraryVersionFilter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public abstract class FrameworkSupportConfigurable implements Disposable {
  private final EventDispatcher<FrameworkSupportConfigurableListener> myDispatcher = EventDispatcher.create(FrameworkSupportConfigurableListener.class);

  public abstract @Nullable JComponent getComponent();

  public abstract void addSupport(@NotNull Module module, @NotNull ModifiableRootModel model, final @Nullable Library library);

  public FrameworkVersion getSelectedVersion() {
    return null;
  }

  public List<? extends FrameworkVersion> getVersions() {
    return Collections.emptyList();
  }

  public void onFrameworkSelectionChanged(boolean selected) {
  }

  public @Nullable FrameworkLibraryVersionFilter getVersionFilter() {
    return FrameworkLibraryVersionFilter.ALL;
  }

  public boolean isVisible() {
    return true;
  }

  public void addListener(@NotNull FrameworkSupportConfigurableListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeListener(@NotNull FrameworkSupportConfigurableListener listener) {
    myDispatcher.removeListener(listener);
  }

  protected void fireFrameworkVersionChanged() {
    myDispatcher.getMulticaster().frameworkVersionChanged();
  }

  @Override
  public void dispose() {
  }
}
