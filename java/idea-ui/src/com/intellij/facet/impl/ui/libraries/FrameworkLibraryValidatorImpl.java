// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.ui.FacetConfigurationQuickFix;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.facet.ui.libraries.FrameworkLibraryValidator;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.ui.configuration.libraries.AddCustomLibraryDialog;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;

public class FrameworkLibraryValidatorImpl extends FrameworkLibraryValidator {
  private final CustomLibraryDescription myLibraryDescription;
  private final LibrariesValidatorContext myContext;
  private final FacetValidatorsManager myValidatorsManager;
  private final String myLibraryCategoryName;

  public FrameworkLibraryValidatorImpl(CustomLibraryDescription libraryDescription,
                                       LibrariesValidatorContext context,
                                       FacetValidatorsManager validatorsManager,
                                       String libraryCategoryName) {
    myLibraryDescription = libraryDescription;
    myContext = context;
    myValidatorsManager = validatorsManager;
    myLibraryCategoryName = libraryCategoryName;
  }

  @Override
  public @NotNull ValidationResult check() {
    final Set<? extends LibraryKind> libraryKinds = myLibraryDescription.getSuitableLibraryKinds();
    final Ref<Boolean> found = Ref.create(false);
    myContext.getRootModel().orderEntries().using(myContext.getModulesProvider()).recursively().librariesOnly().forEachLibrary(library -> {
      if (LibraryPresentationManager.getInstance().isLibraryOfKind(library, myContext.getLibrariesContainer(), libraryKinds)) {
        found.set(true);
        return false;
      }
      return true;
    });
    if (found.get()) return ValidationResult.OK;

    return new ValidationResult(JavaUiBundle.message("label.missed.libraries.text", myLibraryCategoryName), new LibrariesQuickFix(myLibraryDescription));
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
      myValidatorsManager.validate();
    }
  }
}
