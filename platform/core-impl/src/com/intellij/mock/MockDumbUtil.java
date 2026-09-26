// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mock;

import com.intellij.openapi.project.DumbUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class MockDumbUtil implements DumbUtil {

  @Override
  public boolean mayUseIndices(@NotNull Project project) {
    return true;
  }

  @Override
  public String chooseDumbModeMessage(@NotNull String nonLightModeMessage, @NotNull String lightModeMessage) {
    return nonLightModeMessage;
  }
}
