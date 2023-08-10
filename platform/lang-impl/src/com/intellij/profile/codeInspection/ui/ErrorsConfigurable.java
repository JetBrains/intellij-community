// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nullable;

/**
 * Marker interface for the configurable which is used to configure the current inspection profile.
 */
public interface ErrorsConfigurable extends Configurable {
  void selectProfile(InspectionProfileImpl profile);

  void selectInspectionTool(final String selectedToolShortName);

  void selectInspectionGroup(final String[] groupPath);

  @Nullable
  Object getSelectedObject();
}
