// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public sealed interface ModalTaskOwner
  permits ProjectModalTaskOwner,
          ComponentModalTaskOwner,
          GuessModalTaskOwner {

  static @NotNull ModalTaskOwner project(@NotNull Project project) {
    return new ProjectModalTaskOwner(project);
  }

  static @NotNull ModalTaskOwner component(@NotNull Component component) {
    return new ComponentModalTaskOwner(component);
  }

  static @NotNull ModalTaskOwner guess() {
    return GuessModalTaskOwner.INSTANCE;
  }
}
