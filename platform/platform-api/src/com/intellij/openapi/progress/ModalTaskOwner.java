// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.NonExtendable;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

@Experimental
@NonExtendable
public interface ModalTaskOwner {

  static @NotNull ModalTaskOwner project(@NotNull Project project) {
    return ApplicationManager.getApplication().getService(TaskSupport.class).modalTaskOwner(project);
  }

  static @NotNull ModalTaskOwner component(@NotNull Component component) {
    return ApplicationManager.getApplication().getService(TaskSupport.class).modalTaskOwner(component);
  }

  static @NotNull ModalTaskOwner guess() {
    return new ModalTaskOwner() {
    };
  }
}
