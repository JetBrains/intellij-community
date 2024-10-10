// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.util.Objects;

/**
 * Base interface to export a scheme.
 */
public abstract class SchemeExporter<T extends Scheme> {
  /**
   * @deprecated use {@link #exportScheme(Project, Scheme, OutputStream)}.
   */
  @SuppressWarnings({"unused", "RedundantThrows"})
  @ApiStatus.Internal
  @Deprecated
  public void exportScheme(@NotNull T scheme, @NotNull OutputStream outputStream) throws Exception {
  }

  /**
   * Writes a scheme to a given {@code outputStream}.
   *
   * @param scheme       The scheme to export.
   * @param outputStream The output stream to write to.
   * @param project      The optional project, null if there are no open projects.
   * @throws Exception Scheme export exception. Will be reported to UI.
   */
  public void exportScheme(@Nullable Project project, @NotNull T scheme, @NotNull OutputStream outputStream) throws Exception {
    exportScheme(scheme, outputStream);
  }

  /**
   * @return Target file extension without a dot, for example "xml".
   */
  public abstract String getExtension();

  /**
   * @param schemeName The initial scheme display name.
   * @return The default file name to be used.
   */
  public String getDefaultFileName(@NotNull String schemeName) {
    return FileUtil.sanitizeFileName(schemeName);
  }

  /**
   * @return A directory which should be preselected for the given project if any.
   */
  public @NotNull VirtualFile getDefaultDir(@Nullable Project project) {
    return Objects.requireNonNull(VfsUtil.getUserHomeDir());
  }
}
