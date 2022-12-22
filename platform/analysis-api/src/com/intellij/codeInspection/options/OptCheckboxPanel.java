// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record OptCheckboxPanel(@NotNull List<@NotNull OptCheckbox> children) implements OptRegularComponent {
  @Override
  public @NotNull OptCheckboxPanel prefix(@NotNull String bindPrefix) {
    return new OptCheckboxPanel(ContainerUtil.map(children, c -> c.prefix(bindPrefix)));
  }
}
