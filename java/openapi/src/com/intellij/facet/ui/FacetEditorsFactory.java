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

package com.intellij.facet.ui;

import com.intellij.facet.ui.libraries.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class FacetEditorsFactory {
  public static FacetEditorsFactory getInstance() {
    return ServiceManager.getService(FacetEditorsFactory.class);
  }


  public abstract FacetLibrariesValidator createLibrariesValidator(@NotNull LibraryInfo[] libraries, 
                                                                   FacetLibrariesValidatorDescription description,
                                                                   FacetEditorContext context,
                                                                   final FacetValidatorsManager validatorsManager);

  public abstract FacetLibrariesValidator createLibrariesValidator(@NotNull final LibraryInfo[] libraries,
                                                                   @NotNull final Module module,
                                                                   @NotNull final String libraryName);

  public abstract LibrariesValidationComponent createLibrariesValidationComponent(LibraryInfo[] libraryInfos, Module module, 
                                                                         String defaultLibraryName);

  public abstract MultipleFacetEditorHelper createMultipleFacetEditorHelper();
}
