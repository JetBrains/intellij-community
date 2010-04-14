/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.unscramble;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;

/**
 * @author Konstantin Bulenkov
 */
public final class UnscrambleAction extends AnAction implements DumbAware {
  private static final UnscrambleListener LISTENER = new UnscrambleListener();

  static {
    final String key = "analyze.exceptions.on.the.fly";
    final ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    if (Registry.is(key)) {
      app.addApplicationListener(LISTENER);
    }

    Registry.get(key).addListener(new RegistryValueListener.Adapter() {
      public void afterValueChanged(RegistryValue value) {
        if (value.asBoolean()) {
          app.addApplicationListener(LISTENER);
        } else {
          app.removeApplicationListener(LISTENER);
        }
      }
    }, app);
  }
  
  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    new UnscrambleDialog(project).show();
  }

  public void update(AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    presentation.setEnabled(project != null);
  }
}
