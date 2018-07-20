// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.PathUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;

@State(
  name = "RecentProjectsManager",
  storages = {
    @Storage(value = "recentProjects.xml", roamingType = RoamingType.DISABLED),
    @Storage(value = "other.xml", deprecated = true)
  }
)
public class RecentProjectsManagerImpl extends RecentProjectsManagerBase {
  public RecentProjectsManagerImpl(MessageBus messageBus) {
    super(messageBus);
  }

  @Override
  @SystemIndependent
  protected String getProjectPath(@NotNull Project project) {
    return PathUtil.toSystemIndependentName(project.getPresentableUrl());
  }
}
