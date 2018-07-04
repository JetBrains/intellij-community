/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.ui.tree;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author Nikolay.Tropin
 */
public abstract class NodeDescriptorNameAdjuster {
  public static final ExtensionPointName<NodeDescriptorNameAdjuster> EP_NAME = ExtensionPointName.create("com.intellij.debugger.nodeNameAdjuster");

  public abstract boolean isApplicable(@NotNull NodeDescriptor descriptor);

  public abstract String fixName(String name, @NotNull NodeDescriptor descriptor);

  public static NodeDescriptorNameAdjuster findFor(@NotNull NodeDescriptor descriptor) {
    return Arrays.stream(EP_NAME.getExtensions()).filter(adjuster -> adjuster.isApplicable(descriptor)).findFirst().orElse(null);
  }
}
