// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a table of lines, each line is a separate OptStringList control but the elements 
 * in the corresponding lists are synchronized.
 * 
 * @param label label for the whole table
 * @param children list of columns 
 */
public record OptTable(@NotNull LocMessage label, @NotNull List<@NotNull OptStringList> children) implements OptRegularComponent {
  @Override
  public @NotNull OptRegularComponent prefix(@NotNull String bindPrefix) {
    return new OptTable(label, ContainerUtil.map(children, child -> child.prefix(bindPrefix)));
  }
}
