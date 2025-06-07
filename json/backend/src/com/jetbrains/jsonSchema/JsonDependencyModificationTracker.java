// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;

@Service(Service.Level.PROJECT)
public final class JsonDependencyModificationTracker extends SimpleModificationTracker {
  public static JsonDependencyModificationTracker forProject(Project project) {
    return project.getService(JsonDependencyModificationTracker.class);
  }
}
