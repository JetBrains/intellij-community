// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A panel of checkboxes, whose children components are rendered on the right side and only visible when a particular checkbox is
 * selected.
 * @param children checkboxes that appear on the panel
 */
public record OptCheckboxPanel(@NotNull List<@NotNull OptCheckbox> children) implements OptRegularComponent {
  @Override
  public @NotNull OptCheckboxPanel prefix(@NotNull String bindPrefix) {
    return new OptCheckboxPanel(ContainerUtil.map(children, c -> c.prefix(bindPrefix)));
  }
}
