// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.options.OptPane;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

@ApiStatus.Internal
public class RedundantSuppressInspection extends RedundantSuppressInspectionBase {

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("IGNORE_ALL", InspectionsBundle.message("inspection.redundant.suppression.option", "@SuppressWarning(\"ALL\")")));
  }
}
