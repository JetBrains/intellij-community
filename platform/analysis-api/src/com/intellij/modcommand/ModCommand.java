// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A transparent command, which modifies the project/workspace state (writes file, changes setting, moves editor caret, etc.),
 * or produces a user interaction (displays question, launches browser, etc.).
 * <p>
 * All inheritors are records, so the whole state is declarative and readable.
 */
public sealed interface ModCommand
  permits ModChooseAction, ModChooseMember, ModCompositeCommand, ModCopyToClipboard, ModCreateFile, ModDeleteFile, ModDisplayMessage,
          ModHighlight, ModNavigate, ModNothing, ModRenameSymbol, ModShowConflicts, ModStartTemplate, ModUpdateFileText,
          ModUpdateInspectionOptions {

  /**
   * @return true if the command does nothing
   */
  default boolean isEmpty() {
    return false;
  }

  /**
   * @return set of files that are potentially modified by this command
   */
  default @NotNull Set<@NotNull VirtualFile> modifiedFiles() {
    return Set.of();
  }

  /**
   * @param next command to be executed right after current
   * @return the composite command that executes both current and the next command
   */
  default @NotNull ModCommand andThen(@NotNull ModCommand next) {
    if (isEmpty()) return next;
    if (next.isEmpty()) return this;
    List<ModCommand> commands = new ArrayList<>(unpack());
    commands.addAll(next.unpack());
    return commands.size() == 1 ? commands.get(0) : new ModCompositeCommand(commands); 
  }

  /**
   * @return list of individual commands this command consists of
   */
  default @NotNull List<@NotNull ModCommand> unpack() {
    return List.of(this);
  }
}
