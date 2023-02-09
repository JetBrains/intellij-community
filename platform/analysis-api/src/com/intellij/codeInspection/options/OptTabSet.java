// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Set of tabs
 *
 * @param children tab controls 
 */
public record OptTabSet(@NotNull List<@NotNull OptTab> children) implements OptRegularComponent {
  @Override
  public @NotNull OptTabSet prefix(@NotNull String bindPrefix) {
    return new OptTabSet(ContainerUtil.map(children, c -> c.prefix(bindPrefix)));
  }
}
