/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.facet.impl.ui.libraries;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class LibrariesValidatorContextImpl implements LibrariesValidatorContext {
  private final Module myModule;
  private final LibrariesContainer myLibrariesContainer;

  public LibrariesValidatorContextImpl(final @NotNull Module module) {
    myModule = module;
    myLibrariesContainer = LibrariesContainerFactory.createContainer(module);
  }

  @NotNull
  public ModuleRootModel getRootModel() {
    return ModuleRootManager.getInstance(myModule);
  }

  @Nullable
  public ModifiableRootModel getModifiableRootModel() {
    return null;
  }

  @NotNull
  public ModulesProvider getModulesProvider() {
    return new DefaultModulesProvider(myModule.getProject());
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  public LibrariesContainer getLibrariesContainer() {
    return myLibrariesContainer;
  }

}
