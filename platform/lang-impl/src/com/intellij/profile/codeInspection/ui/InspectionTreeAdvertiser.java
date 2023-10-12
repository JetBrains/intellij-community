// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * An extension point to customize the inspection configuration settings page (Preferences | Editor | Inspections).
 */
public abstract class InspectionTreeAdvertiser {
  public static final ExtensionPointName<InspectionTreeAdvertiser> EP_NAME =
    ExtensionPointName.create("com.intellij.inspectionTreeAdvertiser");

  /**
   * Returns additional actions for creating inspections.
   * They appear when no inspections are found after filtering the inspection tree.
   */
  @NotNull
  public abstract List<AnAction> getActions(SingleInspectionProfilePanel panel);

  public record CustomGroup(String[] path, String description) {}

  /**
   * Returns a list of groups that:
   * <li>will always be displayed</li>
   * <li>can have a custom description</li>
   */
  public List<CustomGroup> getCustomGroups() { return new ArrayList<>(); }
}
