/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.options;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;

/**
 * Base interface to export a scheme.
 *
 * @author Rustam Vishnyakov
 */
public abstract class SchemeExporter<T extends Scheme> {
  /**
   * @deprecated use {@link #exportScheme(Project, Scheme, OutputStream)}.
   */
  @SuppressWarnings({"DeprecatedIsStillUsed", "unused", "RedundantThrows"})
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
    return schemeName;
  }

  /**
   * @return A directory which should be preselected for the given project if any.
   */
  @NotNull
  public VirtualFile getDefaultDir(@Nullable Project project) {
    return ObjectUtils.notNull(VfsUtil.getUserHomeDir());
  }
}
