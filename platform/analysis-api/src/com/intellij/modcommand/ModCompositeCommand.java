// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A composite command that contains leaf commands inside
 * 
 * @param commands list of commands; must not contain composite commands
 */
public record ModCompositeCommand(@NotNull List<@NotNull ModCommand> commands) implements ModCommand {
  public ModCompositeCommand {
    commands = List.copyOf(commands);
    for (ModCommand command : commands) {
      if (command instanceof ModCompositeCommand) {
        throw new IllegalArgumentException("Nested composite command");
      }
    }
  }

  @Override
  public boolean isEmpty() {
    return ContainerUtil.all(commands, ModCommand::isEmpty);
  }

  @Override
  public @NotNull Set<@NotNull VirtualFile> modifiedFiles() {
    return commands.stream().flatMap(c -> c.modifiedFiles().stream()).collect(Collectors.toSet());
  }

  @Override
  public @NotNull List<@NotNull ModCommand> unpack() {
    return commands;
  }
}
