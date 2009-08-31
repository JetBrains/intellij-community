/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui.libraries;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface LibrariesValidatorContext {
  @Nullable
  ModuleRootModel getRootModel();

  @Nullable
  ModifiableRootModel getModifiableRootModel();

  @NotNull
  ModulesProvider getModulesProvider();

  @NotNull
  Module getModule();

  LibrariesContainer getLibrariesContainer();

}
