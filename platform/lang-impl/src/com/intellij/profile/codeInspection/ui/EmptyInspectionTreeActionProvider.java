// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An extension point to add actions in the 'Create an inspection…' link dropdown (Preferences | Editor | Inspections).
 * It appears when no inspections are found by search.
 */
public abstract class EmptyInspectionTreeActionProvider {
  public static final ExtensionPointName<EmptyInspectionTreeActionProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.emptyInspectionTreeActionProvider");

  /**
   * Returns additional actions for creating inspections.
   */
  @NotNull
  public abstract List<AnAction> getActions(SingleInspectionProfilePanel panel);
}
