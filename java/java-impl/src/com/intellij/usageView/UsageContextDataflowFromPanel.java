/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.usageView;

import com.intellij.openapi.project.Project;
import com.intellij.usages.UsageContextPanel;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.impl.UsageViewImpl;
import org.jetbrains.annotations.NotNull;

public class UsageContextDataflowFromPanel extends UsageContextDataflowToPanel {
  public static class Provider extends UsageContextDataflowToPanel.Provider {
    @NotNull
    @Override
    public UsageContextPanel create(@NotNull UsageView usageView) {
      return new UsageContextDataflowFromPanel(((UsageViewImpl)usageView).getProject(), usageView.getPresentation());
    }

    @NotNull
    @Override
    public String getTabTitle() {
      return "Dataflow from Here";
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
