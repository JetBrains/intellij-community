/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.platform.ModuleAttachProcessor;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
@State(
  name = "RecentDirectoryProjectsManager",
  storages = {
    @Storage(value = "recentProjectDirectories.xml", roamingType = RoamingType.DISABLED),
    @Storage(value = "other.xml", deprecated = true)
  }
)
public class RecentDirectoryProjectsManagerEx extends RecentDirectoryProjectsManager {
  public RecentDirectoryProjectsManagerEx(MessageBus messageBus) {
    super(messageBus);
  }

  @NotNull
  @Override
  protected String getProjectDisplayName(@NotNull Project project) {
    final String name = ModuleAttachProcessor.getMultiProjectDisplayName(project);
    return name != null ? name : super.getProjectDisplayName(project);
  }
}
