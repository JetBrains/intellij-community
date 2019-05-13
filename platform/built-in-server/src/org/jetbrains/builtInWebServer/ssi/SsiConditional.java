/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.builtInWebServer.ssi;

import io.netty.buffer.ByteBufUtf8Writer;
import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.util.List;

/**
 * SSI command that handles all conditional directives.
 *
 * @author Paul Speed
 * @author David Becker
 */
public class SsiConditional implements SsiCommand {
  @SuppressWarnings("SpellCheckingInspection")
  @Override
  public long process(@NotNull SsiProcessingState ssiProcessingState, @NotNull String commandName, @NotNull List<String> paramNames, @NotNull String[] paramValues, @NotNull ByteBufUtf8Writer writer) {
    // Assume anything using conditionals was modified by it
    long lastModified = System.currentTimeMillis();
    // Retrieve the current state information
    SsiProcessingState.SsiConditionalState state = ssiProcessingState.conditionalState;
    if ("if".equalsIgnoreCase(commandName)) {
      // Do nothing if we are nested in a false branch
      // except count it
      if (state.processConditionalCommandsOnly) {
        state.nestingCount++;
        return lastModified;
      }
      state.nestingCount = 0;
      // Evaluate the expression
      if (evaluateArguments(paramNames, paramValues, ssiProcessingState)) {
        // No more branches can be taken for this if block
        state.branchTaken = true;
      }
      else {
        // Do not process this branch
        state.processConditionalCommandsOnly = true;
        state.branchTaken = false;
      }
    }
    else if ("elif".equalsIgnoreCase(commandName)) {
      // No need to even execute if we are nested in
      // a false branch
      if (state.nestingCount > 0) return lastModified;
      // If a branch was already taken in this if block
      // then disable output and return
      if (state.branchTaken) {
        state.processConditionalCommandsOnly = true;
        return lastModified;
      }
      // Evaluate the expression
      if (evaluateArguments(paramNames, paramValues, ssiProcessingState)) {
        // Turn back on output and mark the branch
        state.processConditionalCommandsOnly = false;
        state.branchTaken = true;
      }
      else {
        // Do not process this branch
        state.processConditionalCommandsOnly = true;
        state.branchTaken = false;
      }
    }
    else if ("else".equalsIgnoreCase(commandName)) {
      // No need to even execute if we are nested in
      // a false branch
      if (state.nestingCount > 0) return lastModified;
      // If we've already taken another branch then
      // disable output otherwise enable it.
      state.processConditionalCommandsOnly = state.branchTaken;
      // And in any case, it's safe to say a branch
      // has been taken.
      state.branchTaken = true;
    }
    else if ("endif".equalsIgnoreCase(commandName)) {
      // If we are nested inside a false branch then pop out
      // one level on the nesting count
      if (state.nestingCount > 0) {
        state.nestingCount--;
        return lastModified;
      }
      // Turn output back on
      state.processConditionalCommandsOnly = false;
      // Reset the branch status for any outer if blocks,
      // since clearly we took a branch to have gotten here
      // in the first place.
      state.branchTaken = true;
    }
    else {
      throw new SsiStopProcessingException();
    }
    return lastModified;
  }

  /**
   * Retrieves the expression from the specified arguments and performs the necessary evaluation steps.
   */
  private static boolean evaluateArguments(@NotNull List<? extends String> names, @NotNull String[] values, @NotNull SsiProcessingState ssiProcessingState) {
    String expression = "expr".equalsIgnoreCase(names.get(0)) ? values[0] : null;
    if (expression == null) {
      throw new SsiStopProcessingException();
    }
    try {
      return new ExpressionParseTree(expression, ssiProcessingState).evaluateTree();
    }
    catch (ParseException e) {
      throw new SsiStopProcessingException();
    }
  }
}