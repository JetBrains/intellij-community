// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.openapi.ui.panel;

import com.intellij.openapi.components.ServiceManager;

import javax.swing.*;

public abstract class JBPanelFactory {
  /**
   * Returns the popup factory instance.
   *
     * @return the popup factory instance.
    */
  public static JBPanelFactory getInstance() {
    return ServiceManager.getService(JBPanelFactory.class);
  }

  public static ComponentPanelBuilder panel(JComponent component) {
    return getInstance().createComponentPanelBuilder(component);
  }

  public static ProgressPanelBuilder panel(JProgressBar progressBar) {
    return getInstance().createProgressPanelBuilder(progressBar);
  }

  public static PanelGridBuilder<ComponentPanelBuilder> componentGrid() {
    return getInstance().createComponentPanelGridBuilder();
  }

  public static PanelGridBuilder<ProgressPanelBuilder> progressGrid() {
    return getInstance().createProgressPanelGridBuilder();
  }

  public abstract ComponentPanelBuilder createComponentPanelBuilder(JComponent component);

  public abstract ProgressPanelBuilder createProgressPanelBuilder(JProgressBar progressBar);

  public abstract PanelGridBuilder<ComponentPanelBuilder> createComponentPanelGridBuilder();

  public abstract PanelGridBuilder<ProgressPanelBuilder> createProgressPanelGridBuilder();
}
