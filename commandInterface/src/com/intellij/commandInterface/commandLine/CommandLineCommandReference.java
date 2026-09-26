// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.commandInterface.command.Command;
import com.intellij.commandInterface.command.Help;
import com.intellij.commandInterface.commandLine.psi.CommandLineCommand;
import com.intellij.commandInterface.commandLine.psi.CommandLineFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ref to be injected in command itself
 *
 * @author Ilya.Kazakevich
 */
final class CommandLineCommandReference extends CommandLineElementReference<CommandLineCommand> {
  CommandLineCommandReference(final @NotNull CommandLineCommand element) {
    super(element);
  }

  @Override
  public @Nullable PsiElement resolve() {
    return null;
  }

  @Override
  public Object @NotNull [] getVariants() {
    final CommandLineFile file = getCommandLineFile();
    if (file == null) {
      return EMPTY_ARRAY;
    }
    final List<Command> commands = file.getCommands();
    if (commands == null) {
      return EMPTY_ARRAY;
    }

    final LookupWithIndentsBuilder result = new LookupWithIndentsBuilder();

    for (final Command command : commands) {
      final LookupElementBuilder lookupElementBuilder = LookupElementBuilder.create(command.getName());
      final Help help = command.getHelp(true);
      result.addElement(lookupElementBuilder, (help != null ? help.getHelpString() : null));
    }


    return result.getResult();
  }
}
