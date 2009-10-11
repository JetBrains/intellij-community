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
package com.intellij.ide.util.frameworkSupport;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;

/**
 * @author nik
 */
public class AddFrameworkSupportAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE_CONTEXT);
    if (module == null) return;
    
    AddFrameworkSupportDialog dialog = AddFrameworkSupportDialog.createDialog(module);
    if (dialog != null) {
      dialog.show();
    }
  }

  public void update(final AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE_CONTEXT);
    boolean enable = module != null && AddFrameworkSupportDialog.isAvailable(module);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(enable);
    }
    else {
      e.getPresentation().setEnabled(enable);
    }
  }
}
