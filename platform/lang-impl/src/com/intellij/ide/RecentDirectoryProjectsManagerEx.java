/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.platform.ModuleAttachProcessor;
import com.intellij.projectImport.ProjectAttachProcessor;
import com.intellij.util.messages.MessageBus;

/**
 * @author yole
 */
@State(
  name = "RecentDirectoryProjectsManager",
  roamingType = RoamingType.DISABLED,
  storages = {
    @Storage(
      file = "$APP_CONFIG$/other.xml"
    )}
)
public class RecentDirectoryProjectsManagerEx extends RecentDirectoryProjectsManager {
  public RecentDirectoryProjectsManagerEx(ProjectManager projectManager, MessageBus messageBus) {
    super(projectManager, messageBus);
  }

  @Override
  protected String getProjectDisplayName(Project project) {
    if (ProjectAttachProcessor.canAttachToProject()) {
      final Module[] modules = ModuleManager.getInstance(project).getModules();
      if (modules.length > 1) {
        Module primaryModule = ModuleAttachProcessor.getPrimaryModule(project);
        if (primaryModule == null) {
          primaryModule = modules [0];
        }
        StringBuilder result = new StringBuilder(primaryModule.getName());
        result.append(", ");
        for (Module module : modules) {
          if (module == primaryModule) continue;
          result.append(module.getName());
          break;
        }
        if (modules.length > 2) {
          result.append("...");          
        }
        return result.toString();
      }
    }
    return super.getProjectDisplayName(project);
  }
}
