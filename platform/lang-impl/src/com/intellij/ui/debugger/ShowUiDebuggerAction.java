/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ui.debugger;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class ShowUiDebuggerAction extends AnAction {

  private UiDebugger myDebugger;

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setText("UI Debugger");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (myDebugger == null) {
      myDebugger = new UiDebugger() {
        @Override
        public void dispose() {
          super.dispose();
          myDebugger = null;
        }
      };
    } else {
      myDebugger.show();
    }
  }
}