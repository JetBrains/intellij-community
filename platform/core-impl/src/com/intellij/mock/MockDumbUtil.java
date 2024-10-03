// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mock;

import com.intellij.openapi.project.DumbUtil;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class MockDumbUtil implements DumbUtil {

  @Override
  public boolean mayUseIndices() {
    return true;
  }
}
