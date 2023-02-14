// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A horizontal stack of controls. By default, the form flows from up to down.
 * Horizontal stack allows to put several controls on the same line.
 *
 * @param children list of child components
 */
public record OptHorizontalStack(@NotNull List<@NotNull OptRegularComponent> children) implements OptRegularComponent {
  @Override
  public @NotNull OptHorizontalStack prefix(@NotNull String bindPrefix) {
    return new OptHorizontalStack(ContainerUtil.map(children, c -> c.prefix(bindPrefix)));
  }
}
