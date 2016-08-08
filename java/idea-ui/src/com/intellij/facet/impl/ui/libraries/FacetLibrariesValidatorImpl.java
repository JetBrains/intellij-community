/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.facet.Facet;
import com.intellij.facet.ui.FacetConfigurationQuickFix;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.facet.ui.libraries.FacetLibrariesValidator;
import com.intellij.facet.ui.libraries.FacetLibrariesValidatorDescription;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.frameworkSupport.OldCustomLibraryDescription;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraries.AddCustomLibraryDialog;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class FacetLibrariesValidatorImpl extends FacetLibrariesValidator {
  private final LibrariesValidatorContext myContext;
  private final FacetValidatorsManager myValidatorsManager;
  private RequiredLibrariesInfo myRequiredLibraries;
  private FacetLibrariesValidatorDescription myDescription;
  private final List<Library> myAddedLibraries = new ArrayList<>();

  public FacetLibrariesValidatorImpl(LibraryInfo[] requiredLibraries, FacetLibrariesValidatorDescription description,
                                     final LibrariesValidatorContext context, FacetValidatorsManager validatorsManager) {
    myContext = context;
    myValidatorsManager = validatorsManager;
    myRequiredLibraries = new RequiredLibrariesInfo(requiredLibraries);
    myDescription = description;
  }

  public void setRequiredLibraries(final LibraryInfo[] requiredLibraries) {
    myRequiredLibraries = new RequiredLibrariesInfo(requiredLibraries);
    onChange();
  }

  public boolean isLibrariesAdded() {
    return false;
  }

  public void setDescription(@NotNull final FacetLibrariesValidatorDescription description) {
    myDescription = description;
    onChange();
  }

  @NotNull
  public ValidationResult check() {
    if (myRequiredLibraries == null) {
      return ValidationResult.OK;
    }

    List<VirtualFile> roots = collectRoots(myContext.getRootModel());
    RequiredLibrariesInfo.RequiredClassesNotFoundInfo info = myRequiredLibraries.checkLibraries(VfsUtil.toVirtualFileArray(roots));
    if (info == null) {
      return ValidationResult.OK;
    }

    String missingJars = IdeBundle.message("label.missed.libraries.prefix") + " " + info.getMissingJarsText();
    LibraryInfo[] missingLibraries = info.getLibraryInfos();
    CustomLibraryDescription description = new OldCustomLibraryDescription(missingLibraries, myDescription.getDefaultLibraryName());
    return new ValidationResult(missingJars, new LibrariesQuickFix(description));
  }

  private void onChange() {
    if (myValidatorsManager != null) {
      myValidatorsManager.validate();
    }
  }

  public void onFacetInitialized(Facet facet) {
    for (Library addedLibrary : myAddedLibraries) {
      myDescription.onLibraryAdded(facet, addedLibrary);
    }
  }

  private List<VirtualFile> collectRoots(final @NotNull ModuleRootModel rootModel) {
    final ArrayList<VirtualFile> roots = new ArrayList<>();
    rootModel.orderEntries().using(myContext.getModulesProvider()).recursively().librariesOnly().forEachLibrary(library -> {
      ContainerUtil.addAll(roots, myContext.getLibrariesContainer().getLibraryFiles(library, OrderRootType.CLASSES));
      return true;
    });
    return roots;
  }

  private class LibrariesQuickFix extends FacetConfigurationQuickFix {
    private CustomLibraryDescription myDescription;

    public LibrariesQuickFix(CustomLibraryDescription description) {
      super(IdeBundle.message("button.fix"));
      myDescription = description;
    }

    public void run(final JComponent place) {
      AddCustomLibraryDialog dialog = AddCustomLibraryDialog.createDialog(myDescription, myContext.getLibrariesContainer(),
                                                                     myContext.getModule(), myContext.getModifiableRootModel(), null);
      dialog.show();
      myAddedLibraries.addAll(dialog.getAddedLibraries());
      onChange();
    }
  }
}
