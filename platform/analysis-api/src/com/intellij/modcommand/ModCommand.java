// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A transparent command, which modifies the project/workspace state (writes file, changes setting, moves editor caret, etc.),
 * or produces a user interaction (displays question, launches browser, etc.).
 * <p>
 * All inheritors are records, so the whole state is declarative and readable.
 */
public sealed interface ModCommand permits ModCompositeCommand, ModNavigate, ModNothing, ModUpdatePsiFile {
  /**
   * Executes the command
   * 
   * @param project current project
   * @return execution status
   */
  @RequiresEdt
  default @NotNull ModStatus execute(@NotNull Project project) {
    return ApplicationManager.getApplication().getService(ModCommandService.class).execute(project, this);
  }

  /**
   * @return true if the command does nothing
   */
  default boolean isEmpty() {
    return false;
  }

  /**
   * Performs preparatory step, if necessary. In particular unlocks necessary files for writing
   * 
   * @return status of execution
   */
  @RequiresEdt
  default @NotNull ModStatus prepare() {
    Set<PsiFile> files = modifiedFiles();
    if (files.isEmpty()) return ModStatus.SUCCESS;
    Project project = ContainerUtil.getFirstItem(files).getProject();
    VirtualFile[] vFiles = ContainerUtil.map2Array(files, VirtualFile.class, PsiFile::getVirtualFile);
    return ReadonlyStatusHandler.ensureFilesWritable(project, vFiles) ? ModStatus.SUCCESS : ModStatus.CANCEL;
  }

  /**
   * @return set of files that are potentially modified by this command
   */
  default @NotNull Set<@NotNull PsiFile> modifiedFiles() {
    return Set.of();
  }

  /**
   * A helper method to implement {@link #andThen(ModCommand)}. Should not be called directly.
   * 
   * @param next command to be executed right after current
   * @return merged command that executes both this and next actions; null if merge is not possible.
   * Here, {@link ModCompositeCommand} is not returned
   * @see #andThen(ModCommand) 
   */
  default @Nullable ModCommand tryMerge(@NotNull ModCommand next) {
    return null;
  }

  /**
   * @param next command to be executed right after current
   * @return the composite command that executes both current and the next command
   */
  default @NotNull ModCommand andThen(@NotNull ModCommand next) {
    if (isEmpty()) return next;
    if (next.isEmpty()) return this;
    ModCommand merged = tryMerge(next);
    if (merged != null) {
      return merged;
    }
    List<ModCommand> commands = new ArrayList<>(unpack());
    ModCommand last = Objects.requireNonNull(ContainerUtil.getLastItem(commands));
    List<ModCommand> nextCommands = next.unpack();
    for (int i = 0; i < nextCommands.size(); i++) {
      ModCommand command = nextCommands.get(i);
      merged = last.tryMerge(command);
      if (merged != null) {
        last = merged;
      } else {
        commands.set(commands.size() - 1, last);
        commands.addAll(nextCommands.subList(i, nextCommands.size()));
        break;
      }
    }
    return commands.size() == 1 ? commands.get(0) : new ModCompositeCommand(commands); 
  }

  /**
   * @return list of individual commands this command consists of
   */
  default @NotNull List<@NotNull ModCommand> unpack() {
    return List.of(this);
  }
}
