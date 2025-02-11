// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A specialized implementation of the {@link ConsoleFolding} class designed to handle
 * the folding of console lines that represent warnings related to restricted method calls of java runner.
 * This is a temporary solution until IDEA starts using signals
 *
 * @deprecated will be deleted when IDEA starts using signals
 */
@ApiStatus.Internal
@Deprecated(forRemoval = true)
public class JavaRunnerRestrictedMethodCallConsoleFolding extends ConsoleFolding {
  private static final List<String> RESTRICTED_CALL_WARNING = List.of(
    "WARNING: A restricted method in java.lang.System has been called",
    "WARNING: java.lang.System::load has been called by com.intellij.rt.execution.application.AppMainV2 in an unnamed module",
    "WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module",
    "WARNING: Restricted methods will be blocked in a future release unless native access is enabled"
  );

  @Override
  public boolean shouldFoldLine(@NotNull Project project, @NotNull String line) {
    return ContainerUtil.or(RESTRICTED_CALL_WARNING, line::startsWith);
  }

  @Override
  public boolean shouldBeAttachedToThePreviousLine() {
    return false;
  }

  @Override
  public @Nullable String getPlaceholderText(@NotNull Project project, @NotNull List<@NotNull String> lines) {
    if (lines.size() == RESTRICTED_CALL_WARNING.size() &&
        StreamEx.of(RESTRICTED_CALL_WARNING).zipWith(lines.stream())
          .allMatch(pair -> pair.getValue().startsWith(pair.getKey()))) {
      return JavaBundle.message("restricted.system.load.is.used");
    }
    return null;
  }
}
