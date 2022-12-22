// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @param label   tab label
 * @param content tab content
 */
public record OptTab(@NotNull LocMessage label, @NotNull List<@NotNull OptRegularComponent> children) implements OptComponent {
  @Override
  public @NotNull OptTab prefix(@NotNull String bindPrefix) {
    return new OptTab(label, ContainerUtil.map(children, c -> c.prefix(bindPrefix)));
  }
}
