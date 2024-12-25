// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.command;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Command option.
 * It may have some long names (like --foo) and short (like -f), help text and arguments (if not flag option)
 *
 * @author Ilya.Kazakevich
 */
public final class Option {
  private final @NotNull List<String> myLongNames = new ArrayList<>();
  private final @NotNull List<String> myShortNames = new ArrayList<>();
  private final @Nullable Pair<Integer, Argument> myArgumentAndQuantity;
  private final @NotNull Help myHelp;

  /**
   * @param argumentAndQuantity if option accepts argument, there should be pair of [argument_quantity, its_type_info]
   * @param help                option help
   * @param shortNames          option short names
   * @param longNames           option long names
   */
  public Option(final @Nullable Pair<Integer, Argument> argumentAndQuantity,
                final @NotNull Help help,
                final @NotNull Collection<String> shortNames,
                final @NotNull Collection<String> longNames) {
   assert (argumentAndQuantity == null || argumentAndQuantity.first > 0): "Illegal args and quantity: " + argumentAndQuantity;
    myArgumentAndQuantity = argumentAndQuantity;
    myShortNames.addAll(shortNames);
    myLongNames.addAll(longNames);
    myHelp = help;
  }

  /**
   * @return Option long names
   */
  public @NotNull List<String> getLongNames() {
    return Collections.unmodifiableList(myLongNames);
  }

  /**
   * @return all option names (long and short)
   */
  public @NotNull List<String> getAllNames() {
    final List<String> result = new ArrayList<>(myLongNames);
    result.addAll(myShortNames);
    return result;
  }

  /**
   * @return Option short names
   */
  public @NotNull List<String> getShortNames() {
    return Collections.unmodifiableList(myShortNames);
  }

  // TODO: USe "known arguments info" to prevent copy/paste
  /**
   * @return if option accepts argument -- pair of [argument_quantity, argument]. Null otherwise.
   * Unlike position argument, option argument is <a href="https://docs.python.org/2/library/optparse.html#terminology">always mandatory</a>
   */
  public @Nullable Pair<Integer, Argument> getArgumentAndQuantity() {
    return myArgumentAndQuantity;
  }

  /**
   * @return Option help
   */
  public @NotNull Help getHelp() {
    return myHelp;
  }
}
