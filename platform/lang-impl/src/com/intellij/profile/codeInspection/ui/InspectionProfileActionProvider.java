// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Allows adding custom actions to the inspection profile toolbar
 * in the "Settings | Editor | Inspections" section.
 * <p/>
 * Register in {@code com.intellij.inspectionProfileActionProvider} extension point.
 *
 * @author Bas Leijdekkers
 */
public abstract class InspectionProfileActionProvider {
  public static final ExtensionPointName<InspectionProfileActionProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.inspectionProfileActionProvider");

  /**
   * @return additional actions to render in the given inspection profile panel
   */
  @NotNull
  public abstract List<AnAction> getActions(@NotNull SingleInspectionProfilePanel panel);
}
