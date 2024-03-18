// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine.psi;

import com.intellij.commandInterface.command.Command;
import com.intellij.commandInterface.commandLine.CommandLineLanguage;
import com.intellij.commandInterface.commandLine.CommandLinePart;
import com.intellij.commandInterface.commandLine.ValidationResult;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Key;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.commandInterface.commandLine.psi.CommandLineArgument;
import com.intellij.commandInterface.commandLine.psi.CommandLineCommand;
import com.intellij.commandInterface.commandLine.psi.CommandLineOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;


/**
 * Gnu command line file (topmost element).
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineFile extends PsiFileBase implements CommandLinePart {
  /**
   * List of commands, available on this file.
   */
  private static final Key<List<Command>> COMMANDS = Key.create("COMMANDS");

  public CommandLineFile(final FileViewProvider provider) {
    super(provider, CommandLineLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return getViewProvider().getFileType();
  }

  @Nullable
  @Override
  public CommandLineFile getCommandLineFile() {
    return this;
  }


  /**
   * @param commands list of commands, available for this file. Better to provide one, if you know
   */
  public void setCommands(@Nullable final List<Command> commands) {
    putCopyableUserData(COMMANDS, commands);
  }

  /**
   * @return List of commands, available on this file. May be null if no info available
   */
  @Nullable
  public List<Command> getCommands() {
    return getCopyableUserData(COMMANDS);
  }


  /**
   * Tries to find real command used in this file.
   * You need to first inject list of {@link #setCommands(List)}.
   *
   * @return Command if found and available, or null if command can't be parsed or bad command.
   */
  @Override
  @Nullable
  public Command findRealCommand() {
    final String command = getCommand();
    final List<Command> commands = getCommands();

    if (commands == null || command == null) {
      return null;
    }
    for (final Command realCommand : commands) {
      if (realCommand.getName().equals(command)) {
        return realCommand;
      }
    }

    return null;
  }

  /**
   * Tries to validate file.
   *
   * @return file validation info or null if file is junk or list of commands is unknown (see {@link #setCommands(List)})
   */
  @Nullable
  public ValidationResult getValidationResult() {
    return ValidationResultImpl.create(this);
  }

  /**
   * @return command (text) typed by user. I.e "my_command" in "my_command --foo --bar"
   */
  @Nullable
  public String getCommand() {
    final CommandLineCommand command = PsiTreeUtil.getChildOfType(this, CommandLineCommand.class);
    if (command != null) {
      return command.getText();
    }
    return null;
  }

  /**
   * @return all arguments from file
   */
  @NotNull
  public Collection<CommandLineArgument> getArguments() {
    return PsiTreeUtil.findChildrenOfType(this, CommandLineArgument.class);
  }

  /**
   * @return all options from file
   */
  @NotNull
  public Collection<CommandLineOption> getOptions() {
    return PsiTreeUtil.findChildrenOfType(this, CommandLineOption.class);
  }
}
