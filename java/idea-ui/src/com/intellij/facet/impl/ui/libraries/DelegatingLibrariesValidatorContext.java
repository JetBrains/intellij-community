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

import com.intellij.facet.impl.ui.FacetEditorContextBase;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class DelegatingLibrariesValidatorContext implements LibrariesValidatorContext {
  private final FacetEditorContext myDelegate;

  public DelegatingLibrariesValidatorContext(final @NotNull FacetEditorContext delegate) {
    myDelegate = delegate;
  }

  @NotNull
  public Module getModule() {
    return myDelegate.getModule();
  }

  public LibrariesContainer getLibrariesContainer() {
    return ((FacetEditorContextBase)myDelegate).getContainer();
  }

  @NotNull
  public ModulesProvider getModulesProvider() {
    return myDelegate.getModulesProvider();
  }

  @Nullable
  public ModifiableRootModel getModifiableRootModel() {
    return myDelegate.getModifiableRootModel();
  }

  @NotNull
  public ModuleRootModel getRootModel() {
    return myDelegate.getRootModel();
  }

}
