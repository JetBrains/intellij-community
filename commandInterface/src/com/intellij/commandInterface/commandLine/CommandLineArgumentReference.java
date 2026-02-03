// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.commandInterface.command.Argument;
import com.intellij.commandInterface.command.Help;
import com.intellij.commandInterface.command.Option;
import com.intellij.commandInterface.commandLine.psi.CommandLineArgument;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Ref to be injected in command line argument
 *
 * @author Ilya.Kazakevich
 */
final class CommandLineArgumentReference extends CommandLineElementReference<CommandLineArgument> {
  CommandLineArgumentReference(final @NotNull CommandLineArgument element) {
    super(element);
  }

  @Override
  public @Nullable PsiElement resolve() {
    return null;
  }


  @Override
  public Object @NotNull [] getVariants() {
    final LookupWithIndentsBuilder builder = new LookupWithIndentsBuilder();
    final Argument argument = getElement().findRealArgument();
    final Option argumentOption = getElement().findOptionForOptionArgument();
    final Collection<String> argumentValues = (argument != null ? argument.getAvailableValues() : null);

    // priority is used to display args before options
    if (argumentValues != null) {
      for (final String value : argumentValues) {
        final Help help = getElement().findBestHelp();
        final String helpText = (help != null ? help.getHelpString() : null);
        builder.addElement(LookupElementBuilder.create(value).withBoldness(true), helpText, 1);
      }
    }


    final ValidationResult validationResult = getValidationResult();
    if (validationResult == null) {
      return EMPTY_ARRAY;
    }

    if (argumentOption == null) { // If not option argument
      for (final Option option : validationResult.getUnusedOptions()) {
        for (final String value : option.getAllNames()) {
          builder.addElement(LookupElementBuilder.create(value), option.getHelp().getHelpString(), 0);
        }
      }
    }
    return builder.getResult();
  }
}
