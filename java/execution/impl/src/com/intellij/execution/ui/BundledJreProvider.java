// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.JdkBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class BundledJreProvider implements JreProvider {
  @NonNls
  public static final String BUNDLED = "Bundled";
  private final JdkBundle myBundle = JdkBundle.createBundled();

  @NotNull
  @Override
  public String getJrePath() {
    assert myBundle != null;
    return getPatchedJrePath(myBundle.getLocation().getPath());
  }

  @NotNull
  public static String getPatchedJrePath(@NotNull String path) {
    if (SystemInfo.isMac && !path.endsWith("/Contents/Home")) {
      path += "/Contents/Home";
    }
    return path;
  }

  @Override
  public String getPresentableName() {
    return ExecutionBundle.message("bundled.jre.name");
  }

  @Override
  public @NonNls String getID() {
    return BUNDLED;
  }

  @Override
  public boolean isAvailable() {
    return Registry.is("ide.java.show.bundled.runtime") && myBundle != null;
  }
}
