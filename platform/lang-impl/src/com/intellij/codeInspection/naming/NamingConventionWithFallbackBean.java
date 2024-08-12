// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.naming;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import org.intellij.lang.annotations.RegExp;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.pane;

public final class NamingConventionWithFallbackBean extends NamingConventionBean {
  public boolean inheritDefaultSettings = false;

  public NamingConventionWithFallbackBean(@RegExp @NonNls String regex, int minLength, int maxLength, String... predefinedNames) {
    super(regex, minLength, maxLength, predefinedNames);
  }

  public boolean isInheritDefaultSettings() {
    return inheritDefaultSettings;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NamingConventionWithFallbackBean bean)) return false;
    if (!super.equals(o)) return false;

    if (inheritDefaultSettings != bean.inheritDefaultSettings) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + (inheritDefaultSettings ? 1 : 0);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      super.getOptionsPane().asCheckbox("inheritDefaultSettings", InspectionsBundle.message("inspection.naming.conventions.option"))
        .description(InspectionsBundle.message("inspection.naming.conventions.option.description"))
    ); 
  }

  @Override
  public @NotNull OptionController getOptionController() {
    return super.getOptionController()
      .onValue("inheritDefaultSettings", () -> !inheritDefaultSettings, val -> inheritDefaultSettings = !val);
  }
}
