// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.hosting;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RepositoryHostingService {
  @NotNull
  String getServiceDisplayName();

  @Nullable
  default RepositoryListLoader getRepositoryListLoader(@NotNull Project project) {return null;}
}
