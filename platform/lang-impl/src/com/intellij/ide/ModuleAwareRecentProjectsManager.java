// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.project.Project;
import com.intellij.platform.ModuleAttachProcessor;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
final class ModuleAwareRecentProjectsManager extends RecentDirectoryProjectsManager {
  ModuleAwareRecentProjectsManager(@NotNull MessageBus messageBus) {
    super(messageBus);
  }

  @NotNull
  @Override
  protected String getProjectDisplayName(@NotNull Project project) {
    final String name = ModuleAttachProcessor.getMultiProjectDisplayName(project);
    return name != null ? name : super.getProjectDisplayName(project);
  }
}