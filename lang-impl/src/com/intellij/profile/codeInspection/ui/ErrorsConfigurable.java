package com.intellij.profile.codeInspection.ui;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nullable;

/**
 * Marker interface for the configurable which is used to configure the current inspection profile. 
 *
 * @author yole
 */
public interface ErrorsConfigurable extends Configurable {
  void selectProfile(final String name);
  void selectInspectionTool(final String selectedToolShortName);
  @Nullable
  Object getSelectedObject();
}
