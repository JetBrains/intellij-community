/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.application;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.project.DumbAwareAction;

/**
 * @author Konstantin Bulenkov
 */
public class ShowExperimentsAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    new ExperimentsDialog(e.getProject()).show();
  }

  @Override
  public void update(AnActionEvent e) {
    boolean hasExperimentalFeatures = Experiments.EP_NAME.getExtensions().length > 0;
    e.getPresentation().setEnabledAndVisible(hasExperimentalFeatures);
  }
}
