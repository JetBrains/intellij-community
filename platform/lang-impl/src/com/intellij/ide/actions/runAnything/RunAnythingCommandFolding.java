// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything;

import com.intellij.execution.ConsoleFolding;
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

@InternalIgnoreDependencyViolation
final class RunAnythingCommandFolding extends ConsoleFolding {
  @Override
  public synchronized boolean shouldFoldLine(@NotNull Project project, @NotNull String line) {
    return RunAnythingUtil.getOrCreateWrappedCommands(project).stream().anyMatch(pair -> pair.first.equals(StringUtil.trim(line)));
  }

  @Override
  public synchronized @Nullable String getPlaceholderText(@NotNull Project project, @NotNull List<String> lines) {
    if (lines.isEmpty()) return null;

    Collection<Pair<String, String>> commands = RunAnythingUtil.getOrCreateWrappedCommands(project);
    for (Pair<String, String> pair : commands) {
      if (pair.first.equals(StringUtil.trim(ContainerUtil.getFirstItem(lines)))) {
        commands.remove(pair);
        return pair.second;
      }
    }

    return null;
  }
}