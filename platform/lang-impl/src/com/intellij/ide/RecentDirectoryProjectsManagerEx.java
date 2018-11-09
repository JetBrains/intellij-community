// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
@State(name = "RecentDirectoryProjectsManager", storages = @Storage(value = "recentProjectDirectories.xml", roamingType = RoamingType.DISABLED))
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
