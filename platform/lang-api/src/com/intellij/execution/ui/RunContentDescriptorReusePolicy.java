// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @see RunContentDescriptor#isContentReuseProhibited()
 */
@ApiStatus.Experimental
public abstract class RunContentDescriptorReusePolicy {

  public static final RunContentDescriptorReusePolicy DEFAULT = new RunContentDescriptorReusePolicy() {
    @Override
    public boolean canBeReusedBy(@NotNull RunContentDescriptor newDescriptor) {
      return true;
    }
  };

  /**
   * @return true if {@link RunContentDescriptor} instance with this policy can be reused by {@code newDescriptor}
   */
  public abstract boolean canBeReusedBy(@NotNull RunContentDescriptor newDescriptor);
}
