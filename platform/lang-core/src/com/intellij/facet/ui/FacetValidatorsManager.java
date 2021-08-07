// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet.ui;

import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;

@ApiStatus.NonExtendable
public interface FacetValidatorsManager {

  void registerValidator(FacetEditorValidator validator, JComponent... componentsToWatch);

  void validate();

}
