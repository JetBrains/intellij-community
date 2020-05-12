// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.refactoring.inline.InlineOptions;

public class MockInlineMethodOptions implements InlineOptions {
  @Override
  public boolean isInlineThisOnly() {
    return false;
  }

  @Override
  public void close(int exitCode) {
  }

  @Override
  public boolean isPreviewUsages() {
    return false;
  }
}
