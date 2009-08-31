/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.util.newProjectWizard;

import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class FrameworkSupportConfigurable {
  @NonNls public static final String DEFAULT_LIBRARY_NAME = "lib";

  @Nullable
  public abstract JComponent getComponent();

  public abstract void addSupport(Module module, ModifiableRootModel model, final @Nullable Library library);

  @NonNls @NotNull
  public String getLibraryName() {
    return DEFAULT_LIBRARY_NAME;
  }

  @NotNull
  public LibraryInfo[] getLibraries() {
    return LibraryInfo.EMPTY_ARRAY;
  }
}
