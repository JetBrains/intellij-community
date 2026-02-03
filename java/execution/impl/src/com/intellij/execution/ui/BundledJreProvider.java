// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.AlternativeJrePathConverter;
import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public final class BundledJreProvider implements JreProvider {
  public static final String BUNDLED = "Bundled";

  private final @Nullable String myBundle = AlternativeJrePathConverter.BUNDLED_JRE_PATH.getValue();

  @Override
  public @NotNull String getJrePath() {
    assert myBundle != null;
    return myBundle;
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
