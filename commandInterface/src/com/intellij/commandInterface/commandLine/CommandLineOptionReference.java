// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.commandInterface.command.Option;
import com.intellij.commandInterface.commandLine.psi.CommandLineOption;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ref to be injected into command line option
 *
 * @author Ilya.Kazakevich
 */
final class CommandLineOptionReference extends CommandLineElementReference<CommandLineOption> {
  CommandLineOptionReference(final @NotNull CommandLineOption element) {
    super(element);
  }

  @Override
  public @Nullable PsiElement resolve() {
    return null;
  }


  @Override
  public Object @NotNull [] getVariants() {
    final LookupWithIndentsBuilder builder = new LookupWithIndentsBuilder();

    final ValidationResult validationResult = getValidationResult();
    if (validationResult == null) {
      return EMPTY_ARRAY;
    }

    for (final Option option : validationResult.getUnusedOptions()) {
      // Suggest long options for -- and short for -
      final List<String> names = getElement().isLong() ? option.getLongNames() : option.getShortNames();
      for (final String optionName : names) {
        builder.addElement(LookupElementBuilder.create(optionName), option.getHelp().getHelpString());
      }
    }

    return builder.getResult();
  }
}
