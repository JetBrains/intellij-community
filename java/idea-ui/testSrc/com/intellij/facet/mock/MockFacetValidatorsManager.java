// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet.mock;

import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class MockFacetValidatorsManager implements FacetValidatorsManager {
  private String myErrorMessage;
  private final List<FacetEditorValidator> myValidators = new ArrayList<>();

  @Override
  public void registerValidator(final FacetEditorValidator validator, final JComponent... componentsToWatch) {
    myValidators.add(validator);
  }

  @Override
  public void validate() {
    myErrorMessage = null;
    for (FacetEditorValidator validator : myValidators) {
      ValidationResult result = validator.check();
      if (!result.isOk()) {
        myErrorMessage = result.getErrorMessage();
        return;
      }
    }
  }

  @Nullable
  public String getErrorMessage() {
    return myErrorMessage;
  }
}
