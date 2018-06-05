// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.module.Module;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ExternalSystemListener {
  Topic<ExternalSystemListener> TOPIC = Topic.create("External System import notifications", ExternalSystemListener.class);

  void importFinished(@NotNull ProjectData importedProjects, @NotNull List<Module> newModules);
}
