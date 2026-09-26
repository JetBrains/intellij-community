// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A group of controls with a name.
 *
 * @param label label to display above the group
 * @param children list of child components
 */
public record OptGroup(@NotNull LocMessage label, @NotNull List<@NotNull OptRegularComponent> children) implements OptRegularComponent {
  @Override
  public @NotNull OptGroup prefix(@NotNull String bindPrefix) {
    return new OptGroup(label, ContainerUtil.map(children, c -> c.prefix(bindPrefix)));
  }
}
