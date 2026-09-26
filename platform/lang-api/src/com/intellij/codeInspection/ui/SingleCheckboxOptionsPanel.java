// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.NlsContexts;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SingleCheckboxOptionsPanel extends InspectionOptionsPanel {

  public SingleCheckboxOptionsPanel(@NotNull @NlsContexts.Checkbox String label,
                                    @NotNull InspectionProfileEntry owner,
                                    @Language("jvm-field-name") @NonNls String property) {
    super(owner);
    addCheckbox(label, property);
  }
}