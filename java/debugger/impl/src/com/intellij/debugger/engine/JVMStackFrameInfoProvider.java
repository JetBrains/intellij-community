// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.xdebugger.frame.XStackFrameVisibilityInfoProvider;

public interface JVMStackFrameInfoProvider extends XStackFrameVisibilityInfoProvider {
  boolean isSynthetic();

  boolean isInLibraryContent();

  @Override
  default boolean shouldHide() {
    return isSynthetic() || isInLibraryContent();
  }
}
