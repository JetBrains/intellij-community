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
package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleManagerImpl;

/**
 * @author yole
 */
public class NewModuleInGroupAction extends NewModuleAction {
  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    final ModuleGroup[] moduleGroups = ModuleGroup.ARRAY_DATA_KEY.getData(e.getDataContext());
    final Module[] modules = e.getData(DataKeys.MODULE_CONTEXT_ARRAY);
    e.getPresentation().setVisible((moduleGroups != null && moduleGroups.length > 0) ||
                                   (modules != null && modules.length > 0));
  }

  @Override
  protected Object prepareDataFromContext(final AnActionEvent e) {
    final ModuleGroup[] moduleGroups = ModuleGroup.ARRAY_DATA_KEY.getData(e.getDataContext());
    if (moduleGroups != null && moduleGroups.length > 0) {
      return moduleGroups [0];
    }
    return null;
  }

  @Override
  protected void processCreatedModule(final Module module, final Object dataFromContext) {
    ModuleGroup group = (ModuleGroup) dataFromContext;
    if (group != null) {
      ModuleManagerImpl.getInstanceImpl(module.getProject()).setModuleGroupPath(module, group.getGroupPath());
    }
  }
}
