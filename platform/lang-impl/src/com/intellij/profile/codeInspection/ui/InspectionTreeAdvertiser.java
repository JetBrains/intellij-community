// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection.ui;

import com.intellij.openapi.extensions.ExtensionPointName;

import java.util.List;

/**
 * An extension point to customize the inspection configuration settings page (Preferences | Editor | Inspections).
 */
public abstract class InspectionTreeAdvertiser {
  public static final ExtensionPointName<InspectionTreeAdvertiser> EP_NAME =
    ExtensionPointName.create("com.intellij.inspectionTreeAdvertiser");

  public record CustomGroup(String[] path, String description) {}

  /**
   * Returns a list of groups that:
   * <li>will always be displayed</li>
   * <li>can have a custom description</li>
   */
  public List<CustomGroup> getCustomGroups() { return List.of(); }
}
