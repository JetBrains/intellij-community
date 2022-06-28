// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface GitSilentFileAdderProvider {
  ProjectExtensionPointName<GitSilentFileAdderProvider> EP_NAME = new ProjectExtensionPointName<>("com.intellij.gitSilentFileAdder");

  @NotNull
  GitSilentFileAdder create();

  @NotNull
  static GitSilentFileAdder create(@NotNull Project project) {
    return EP_NAME.extensions(project).findFirst().map(it -> it.create()).orElse(new GitSilentFileAdder.Empty());
  }
}
