// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.console;

import com.intellij.execution.ConsoleFolding;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class SubstringConsoleFolding extends ConsoleFolding {
  @Override
  public boolean shouldFoldLine(@NotNull Project project, @NotNull String line) {
    return ConsoleFoldingSettings.getSettings().shouldFoldLine(line);
  }

  @Override
  public String getPlaceholderText(@NotNull Project project, @NotNull List<String> lines) {
    return LangBundle.message("x.internal.lines", lines.size());
  }

  @Override
  public int getNestingPriority() {
    // This folding is not very intelligent, lower its priority to not break more sophisticated ones.
    return super.getNestingPriority() - 10;
  }
}
