package com.intellij.execution;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public abstract class ConsoleFolding {
  public static final ExtensionPointName<ConsoleFolding> EP_NAME = ExtensionPointName.create("com.intellij.console.folding");

  public boolean shouldFoldLine(@NotNull Project project, @NotNull String line) {
    return shouldFoldLine(line);
  }

  @Nullable
  public String getPlaceholderText(@NotNull Project project, @NotNull List<String> lines) {
    return getPlaceholderText(lines);
  }

  /**
   * Deprecated since 2018.1. Use {@link #shouldFoldLine(Project, String)} instead.
   *
   * @param line to check if should be folded
   * @return true is line should be folded, false if not
   */
  @Deprecated
  public boolean shouldFoldLine(@NotNull String line) { return false; }

  /**
   * Deprecated since 2018.1. Use {@link #getPlaceholderText(Project, List)} instead.
   *
   * @param lines to fold
   * @return placeholder for lines
   */
  @Deprecated
  @Nullable
  public String getPlaceholderText(@NotNull List<String> lines) { return null; }
}
