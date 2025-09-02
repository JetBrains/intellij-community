// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataMap;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ProjectFileDirectoryRule {
  static @Nullable VirtualFile getData(@NotNull DataMap dataProvider) {
    Project project = dataProvider.get(CommonDataKeys.PROJECT);
    return project == null ? null : project.getBaseDir();
  }
}