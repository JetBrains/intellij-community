// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkUpdateCheckContributor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class AlternativeSdkRootsProviderForJdkUpdate implements JdkUpdateCheckContributor {
  @NotNull
  @Override
  public List<Sdk> contributeJdks(@NotNull Project project) {
    return AlternativeSdkRootsProvider.getAdditionalProjectJdks(project);
  }
}
