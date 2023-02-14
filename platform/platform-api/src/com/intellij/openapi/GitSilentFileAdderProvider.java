// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * To be used in async Project initialization tasks to add files silently,
 * preventing triggering of 'add new files to vcs' notifications/dialogs
 * by {@link com.intellij.openapi.vcs.VcsVFSListener}.
 *
 * @see GitRepositoryInitializer
 * @see com.intellij.openapi.vcs.VcsFileListenerContextHelper
 */
@ApiStatus.Internal
public interface GitSilentFileAdderProvider {
  ProjectExtensionPointName<GitSilentFileAdderProvider> EP_NAME = new ProjectExtensionPointName<>("com.intellij.gitSilentFileAdder");

  @NotNull
  GitSilentFileAdder create();

  @NotNull
  static GitSilentFileAdder create(@NotNull Project project) {
    return EP_NAME.getExtensions(project).stream().findFirst().map(it -> it.create()).orElse(new GitSilentFileAdder.Empty());
  }
}
