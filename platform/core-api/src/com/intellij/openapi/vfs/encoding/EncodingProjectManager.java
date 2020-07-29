// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

public abstract class EncodingProjectManager extends EncodingManager {
  public static EncodingProjectManager getInstance(@NotNull Project project) {
    return project.getService(EncodingProjectManager.class);
  }

  /**
   * @return Project encoding name (configured in Settings|File Encodings|Project Encoding) or empty string if it's configured to "System Default"
   */
  @NotNull
  @Override
  public abstract String getDefaultCharsetName();

  /**
   * @return Project encoding (configured in Settings|File Encodings|Project Encoding)
   */
  @NotNull
  @Override
  public abstract Charset getDefaultCharset();

  /**
   * Sets Project encoding (configured in Settings|File Encodings|Project Encoding). Use empty string to specify "System Default"
   */
  @Override
  public abstract void setDefaultCharsetName(@NotNull String name);
}
