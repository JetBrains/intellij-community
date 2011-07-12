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

package com.intellij.facet.impl.ui;

import com.intellij.facet.impl.ui.libraries.*;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorsFactory;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.MultipleFacetEditorHelper;
import com.intellij.facet.ui.libraries.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class FacetEditorsFactoryImpl extends FacetEditorsFactory {
  public static FacetEditorsFactoryImpl getInstanceImpl() {
    return (FacetEditorsFactoryImpl)getInstance();
  }

  public FrameworkLibraryValidator createLibraryValidator(@NotNull CustomLibraryDescription libraryDescription,
                                                          @NotNull FacetEditorContext context,
                                                          @NotNull FacetValidatorsManager validatorsManager,
                                                          @NotNull String libraryCategory) {
    return createLibraryValidator(libraryDescription, new DelegatingLibrariesValidatorContext(context), validatorsManager, libraryCategory);
  }
  public FrameworkLibraryValidator createLibraryValidator(@NotNull CustomLibraryDescription libraryDescription,
                                                          @NotNull LibrariesValidatorContext context,
                                                          @NotNull FacetValidatorsManager validatorsManager,
                                                          @NotNull String libraryCategory) {
    return new FrameworkLibraryValidatorImpl(libraryDescription, context, validatorsManager, libraryCategory);
  }

  public FacetLibrariesValidator createLibrariesValidator(@NotNull final LibraryInfo[] libraries, final FacetLibrariesValidatorDescription description,
                                                          final FacetEditorContext context,
                                                          final FacetValidatorsManager validatorsManager) {
    return new FacetLibrariesValidatorImpl(libraries, description, new DelegatingLibrariesValidatorContext(context), validatorsManager);
  }

  public FacetLibrariesValidator createLibrariesValidator(@NotNull final LibraryInfo[] libraries, @NotNull final Module module, @NotNull final String libraryName) {
    return new FacetLibrariesValidatorImpl(libraries, new FacetLibrariesValidatorDescription(libraryName), new LibrariesValidatorContextImpl(module), null);
  }

  public LibrariesValidationComponent createLibrariesValidationComponent(LibraryInfo[] libraryInfos, Module module,
                                                                         String defaultLibraryName) {
    return new LibrariesValidationComponentImpl(libraryInfos, module, defaultLibraryName);
  }

  public MultipleFacetEditorHelper createMultipleFacetEditorHelper() {
    return new MultipleFacetEditorHelperImpl();
  }
}
