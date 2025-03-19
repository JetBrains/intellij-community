// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine.psi;

import com.intellij.commandInterface.command.Argument;
import com.intellij.commandInterface.command.Command;
import com.intellij.commandInterface.command.Option;
import com.intellij.commandInterface.commandLine.ValidationResult;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Validation result provider and holder implemented as visitor.
 * @author Ilya.Kazakevich
 */
final class ValidationResultImpl extends CommandLineVisitor implements ValidationResult {
  /**
   * All options [name -> option]
   */
  private final @NotNull Map<String, Option> myOptions = new HashMap<>();
  /**
   * Available, but unused options [name -> option]
   */
  private final @NotNull Map<String, Option> myUnusedOptions = new HashMap<>();
  /**
   * We always need command to validate args
   */
  private final @NotNull Command myCommand;
  /**
   * Number of next positional argument. I.e. will be 3 for "my_arg arg_1 arg_2"
   */
  private int myCurrentPositionArgument;
  /**
   * If next arg is supposed to be option arg, then option and number of expected args stored here.
   * Null stored otherwise.
   */
  private @Nullable Pair<Option, Integer> myCurrentOptionAndArgsLeft;
  /**
   * List of elements whose values are known to be bad
   */
  private final @NotNull Collection<PsiElement> myBadValues = new ArrayList<>();
  /**
   * List of elements which is known to be excess
   */
  private final @NotNull Collection<CommandLineArgument> myExcessArguments = new ArrayList<>();
  /**
   * Map of arguments known to be option arguments [PSI argument -> option]
   */
  private final @NotNull Map<CommandLineArgument, Option> myOptionArguments = new HashMap<>();
  /**
   * PSI argument -> argument map
   */
  private final @NotNull Map<CommandLineArgument, Argument> myArguments = new HashMap<>();

  private ValidationResultImpl(final @NotNull Command command) {
    for (final Option option : command.getOptions()) {
      for (final String optionName : option.getAllNames()) {
        myOptions.put(optionName, option);
      }
    }
    myUnusedOptions.putAll(myOptions);
    myCommand = command;
  }

  @Override
  public boolean isBadValue(final @NotNull PsiElement element) {
    return myBadValues.contains(element);
  }

  @Override
  public boolean isExcessArgument(final @NotNull CommandLineArgument argument) {
    return myExcessArguments.contains(argument);
  }

  @Override
  public @NotNull Collection<Option> getUnusedOptions() {
    return myUnusedOptions.values();
  }


  @Override
  public @Nullable Option getOptionForOptionArgument(final @NotNull CommandLineArgument argument) {
    return myOptionArguments.get(argument);
  }

  @Override
  public @Nullable Argument getArgument(final @NotNull CommandLineArgument commandLineArgument) {
    return myArguments.get(commandLineArgument);
  }

  @Override
  public @Nullable Option getOption(final @NotNull CommandLineOption option) {
    return myOptions.get(option.getOptionName());
  }

  /**
   * Creates validation result by file
   * @param file file to validate
   * @return validation result or null if file has no command or command is unknown
   */
  static @Nullable ValidationResult create(final CommandLineFile file) {
    final Command command = file.findRealCommand();
    if (command == null) {
      return null;
    }
    final ValidationResultImpl validationLayout = new ValidationResultImpl(command);
    file.acceptChildren(validationLayout);
    return validationLayout;
  }

  @Override
  public void visitArgument(final @NotNull CommandLineArgument o) {
    super.visitArgument(o);
    if (myCurrentOptionAndArgsLeft != null) {
      processOptionArgument(o);
      return;
    }

    // Process as positional
    processPositionalArgument(o);
  }

  private void processPositionalArgument(final @NotNull CommandLineArgument o) {
    final Pair<Boolean, Argument> argumentPair = myCommand.getArgumentsInfo().getArgument(myCurrentPositionArgument++);
    if (argumentPair == null) {
      myExcessArguments.add(o);
    }
    else {
      processArgument(o, argumentPair.second);
    }
  }

  private void processOptionArgument(final @NotNull CommandLineArgument o) {
    assert myCurrentOptionAndArgsLeft != null: "Method can't be called if no current option exist";
    if (myCurrentOptionAndArgsLeft.second > 0) {
      myCurrentOptionAndArgsLeft = Pair.create(myCurrentOptionAndArgsLeft.first, myCurrentOptionAndArgsLeft.second - 1);
      final Pair<Integer, Argument> argumentAndQuantity = myCurrentOptionAndArgsLeft.first.getArgumentAndQuantity();
      // TODO: Use class instead of pair to prevent such a stupid checks
      assert argumentAndQuantity != null: "Option has arguments left but no argument info";
      final Argument argumentInfo = argumentAndQuantity.getSecond();
      processArgument(o, argumentInfo);

      myOptionArguments.put(o, myCurrentOptionAndArgsLeft.first);
    }
    else if (myCurrentOptionAndArgsLeft.second == 0) {
      myCurrentOptionAndArgsLeft = null;
      myExcessArguments.add(o);
    }
  }

  private void processArgument(final @NotNull CommandLineArgument o, final Argument argumentInfo) {
    myArguments.put(o, argumentInfo);
    if (!argumentInfo.isValid(o.getText())) {
      myBadValues.add(o);
    }
  }


  @Override
  public void visitWhiteSpace(final @NotNull PsiWhiteSpace space) {
    super.visitWhiteSpace(space);
    // -aSHORT_OPT_ARGUMENT, but -a NEW_POSITION_ARGUMENT, so whitespace makes sense
    if (myCurrentOptionAndArgsLeft != null && myCurrentOptionAndArgsLeft.second == 0) {
      myCurrentOptionAndArgsLeft = null;
    }
  }

  @Override
  public void visitOption(final @NotNull CommandLineOption o) {
    super.visitOption(o);
    if (myUnusedOptions.containsKey(o.getOptionName())) {
      // Remove from list of available options
      final Option option = myUnusedOptions.remove(o.getOptionName());
      for (final String optionName : option.getAllNames()) {
        myUnusedOptions.remove(optionName);
      }

      final Pair<Integer, Argument> argumentAndQuantity = option.getArgumentAndQuantity();
      if (argumentAndQuantity != null) {
        myCurrentOptionAndArgsLeft = Pair.create(option, argumentAndQuantity.first);
      }
      else {
        myCurrentOptionAndArgsLeft = new Pair<>(option, 0);
      }
    }
    else {
      myBadValues.add(o); //No such option available
    }
  }

  @Override
  public @Nullable Pair<Boolean, Argument> getNextArg() {
    if (myCurrentOptionAndArgsLeft != null && myCurrentOptionAndArgsLeft.second > 0) { // Next arg is option arg
      final Pair<Integer, Argument> argumentAndQuantity = myCurrentOptionAndArgsLeft.first.getArgumentAndQuantity();
      if (argumentAndQuantity != null) {
        // Option argument is always mandatory: https://docs.python.org/2/library/optparse.html#terminology
        return Pair.create(true, argumentAndQuantity.second);
      }
    }
    return myCommand.getArgumentsInfo().getArgument(myCurrentPositionArgument);
  }
}
