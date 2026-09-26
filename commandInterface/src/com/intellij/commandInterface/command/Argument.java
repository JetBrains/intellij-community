// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.command;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

// TODO: Support regex validation as well

/**
 * Command argument (positional or option argument)
 * This class represents command argument, not its value.
 *
 * @author Ilya.Kazakevich
 */
public final class Argument {
  /**
   * Argument help user-readable text
   */
  private final @NotNull Help myHelpText;
  /**
   * List of values argument may have. Null if any value is possible.
   * Second argument is True if values can <strong>only</strong> be one from these values, or false if any value actually supported
   */
  private final @Nullable Pair<List<String>, Boolean> myAvailableValues;
  private final @Nullable ArgumentType myType;


  /**
   * @param helpText Argument help user-readable text
   */
  public Argument(final @NotNull Help helpText) {
    this(helpText, null, null);
  }

  /**
   * @param type Argument value type. Null if any type is possible.
   */
  public Argument(final @NotNull ArgumentType type) {
    this(new Help(""), type);
  }

  /**
   * @param helpText Argument help user-readable text
   * @param type     Argument value type. Null if any type is possible.
   */
  public Argument(final @NotNull Help helpText,
                  final @Nullable ArgumentType type) {
    this(helpText, null, type);
  }

  /**
   * @param availableValues List of values argument may have. Null if any value is possible.
   *                        Second argument is True if values can <strong>only</strong> be one from these values, or false if any value actually supported
   */
  public Argument(final @Nullable Pair<List<String>, Boolean> availableValues) {
    this(new Help(""), availableValues, null);
  }

  /**
   * @param helpText        Argument help user-readable text
   * @param availableValues List of values argument may have. Null if any value is possible.
   *                        Second argument is True if values can <strong>only</strong>be one from these values, or false if any value actually supported
   */
  public Argument(final @NotNull Help helpText, final @NotNull Pair<List<String>, Boolean> availableValues) {
    this(helpText, availableValues, null);
  }


  /**
   * @param helpText        Argument help user-readable text
   * @param availableValues List of values argument may have. Null if any value is possible.
   *                        Second argument is True if values can <strong>only</strong> be one from these values, or false if any value actually supported
   * @param type            Argument value type. Null if any type is possible.
   */
  public Argument(final @NotNull Help helpText,
                  final @Nullable Pair<List<String>, Boolean> availableValues,
                  final @Nullable ArgumentType type) {
    myHelpText = helpText;
    myAvailableValues =
      (availableValues == null ? null : Pair.create(Collections.unmodifiableList(availableValues.first), availableValues.second));
    myType = type;
  }

  /**
   * @return Argument help user-readable text
   */
  public @NotNull Help getHelp() {
    return myHelpText;
  }

  /**
   * @return List of values argument may have. Null if any value is possible.
   */
  public @Nullable List<String> getAvailableValues() {
    return (myAvailableValues == null ? null : Collections.unmodifiableList(myAvailableValues.first));
  }


  /**
   * Validates argument value. Argument tries its best to validate value based on information, provided by constructor.
   *
   * @param value value to check
   * @return true if argument may have this value.
   */
  public boolean isValid(final @NotNull String value) {
    if (!isTypeValid(value)) {
      return false;
    }

    if (myAvailableValues == null || !myAvailableValues.second) { // If no available values or any value is allowed
      return true;
    }
    return myAvailableValues.first.contains(value);
  }

  /**
   * Ensures value conforms type (if known)
   *
   * @param value value to check
   * @return false if type is known and it differs from value
   */
  private boolean isTypeValid(final @NotNull String value) {
    // We only check integer for now
    if (myType == ArgumentType.INTEGER) {
      try {
        // We just getCommandLineInfo it to get exception
        Integer.parseInt(value);
      }
      catch (final NumberFormatException ignored) {
        return false;
      }
    }
    return true;
  }
}
