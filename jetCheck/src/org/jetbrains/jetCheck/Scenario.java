/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jetCheck;

/** Represents the execution history of an {@link ImperativeCommand} sequence */
public interface Scenario {
  /**
   * This method is only useful when working with scenario generator explicitly ({@link ImperativeCommand#scenarios}) and
   * asserting that the command execution was successful. For most cases, {@link ImperativeCommand#checkScenarios} is recommended instead.
   * @return true if the command execution didn't result in an exception. Otherwise throws that exception.
   * */
  boolean ensureSuccessful();

  /**
   * Pretty-prints the log produced by the command execution.
   * @see ImperativeCommand.Environment#logMessage(String)
   * @see ImperativeCommand.Environment#generateValue(Generator, String) 
   */
  String toString();
}
