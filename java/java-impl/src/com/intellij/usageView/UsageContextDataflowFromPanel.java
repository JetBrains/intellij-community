// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.usageView;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.usages.UsageContextPanel;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.impl.UsageViewImpl;
import org.jetbrains.annotations.NotNull;

public class UsageContextDataflowFromPanel extends UsageContextDataflowToPanel {
  public static final class Provider extends UsageContextDataflowToPanel.Provider {
    @Override
    public @NotNull UsageContextPanel create(@NotNull UsageView usageView) {
      return new UsageContextDataflowFromPanel(((UsageViewImpl)usageView).getProject(), usageView.getPresentation());
    }

    @Override
    public @NotNull String getTabTitle() {
      return JavaBundle.message("dataflow.from.here");
    }
  }

  public UsageContextDataflowFromPanel(@NotNull Project project, @NotNull UsageViewPresentation presentation) {
    super(project, presentation);
  }


  @Override
  protected boolean isDataflowToThis() {
    return false;
  }
}
