/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.util.frameworkSupport;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class FrameworkSupportConfigurable {
  private final EventDispatcher<FrameworkSupportConfigurableListener> myDispatcher = EventDispatcher.create(FrameworkSupportConfigurableListener.class);

  @Nullable
  public abstract JComponent getComponent();

  public abstract void addSupport(@NotNull Module module, @NotNull ModifiableRootModel model, final @Nullable Library library);

  @SuppressWarnings({"ConstantConditions"})
  public FrameworkVersion getSelectedVersion() {
    return null;
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
}
