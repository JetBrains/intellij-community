// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to adjust variables names, for example trim outer class variable name prefix in java classes
 */
public abstract class NodeDescriptorNameAdjuster {
  public static final ExtensionPointName<NodeDescriptorNameAdjuster> EP_NAME = ExtensionPointName.create("com.intellij.debugger.nodeNameAdjuster");

  public abstract boolean isApplicable(@NotNull NodeDescriptor descriptor);

  public abstract String fixName(String name, @NotNull NodeDescriptor descriptor);

  public static NodeDescriptorNameAdjuster findFor(@NotNull NodeDescriptor descriptor) {
    return EP_NAME.getExtensionList().stream().filter(adjuster -> adjuster.isApplicable(descriptor)).findFirst().orElse(null);
  }
}
