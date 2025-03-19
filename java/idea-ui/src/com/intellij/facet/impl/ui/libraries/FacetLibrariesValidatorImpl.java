// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.Facet;
import com.intellij.facet.ui.FacetConfigurationQuickFix;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.facet.ui.libraries.FacetLibrariesValidator;
import com.intellij.facet.ui.libraries.FacetLibrariesValidatorDescription;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.frameworkSupport.OldCustomLibraryDescription;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraries.AddCustomLibraryDialog;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

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

  @Override
  public void setRequiredLibraries(final LibraryInfo[] requiredLibraries) {
    myRequiredLibraries = new RequiredLibrariesInfo(requiredLibraries);
    onChange();
  }

  @Override
  public boolean isLibrariesAdded() {
    return false;
  }

  @Override
  public void setDescription(final @NotNull FacetLibrariesValidatorDescription description) {
    myDescription = description;
    onChange();
  }

  @Override
  public @NotNull ValidationResult check() {
    if (myRequiredLibraries == null) {
      return ValidationResult.OK;
    }

    List<VirtualFile> roots = collectRoots(myContext.getRootModel());
    RequiredLibrariesInfo.RequiredClassesNotFoundInfo info = myRequiredLibraries.checkLibraries(VfsUtilCore.toVirtualFileArray(roots));
    if (info == null) {
      return ValidationResult.OK;
    }

    String missingJars = JavaUiBundle.message("label.missed.libraries.prefix") + " " + info.getMissingJarsText();
    LibraryInfo[] missingLibraries = info.getLibraryInfos();
    CustomLibraryDescription description = new OldCustomLibraryDescription(missingLibraries, myDescription.getDefaultLibraryName());
    return new ValidationResult(missingJars, new LibrariesQuickFix(description));
  }

  private void onChange() {
    if (myValidatorsManager != null) {
      myValidatorsManager.validate();
    }
  }

  @Override
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
    private final CustomLibraryDescription myDescription;

    LibrariesQuickFix(CustomLibraryDescription description) {
      super(IdeBundle.message("button.fix"));
      myDescription = description;
    }

    @Override
    public void run(final JComponent place) {
      AddCustomLibraryDialog dialog = AddCustomLibraryDialog.createDialog(myDescription, myContext.getLibrariesContainer(),
                                                                     myContext.getModule(), myContext.getModifiableRootModel(), null);
      dialog.show();
      myAddedLibraries.addAll(dialog.getAddedLibraries());
      onChange();
    }
  }
}
