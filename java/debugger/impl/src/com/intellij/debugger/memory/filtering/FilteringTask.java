/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.memory.filtering;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.memory.ui.JavaReferenceInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class FilteringTask implements Runnable {
  private final ValuesList myValues;
  private final ConditionChecker myChecker;
  private final FilteringTaskCallback myCallback;

  private volatile boolean myIsCancelled;

  public FilteringTask(@NotNull String className, @NotNull DebugProcessImpl debugProcess,
                       @NotNull XExpression expression, @NotNull ValuesList values,
                       @NotNull FilteringTaskCallback callback) {
    myChecker = isEmptyFilter(expression)
                ? ConditionChecker.ALL_MATCHED_CHECKER
                : new ConditionCheckerImpl(debugProcess, expression, className);
    myValues = values;
    myCallback = callback;
  }

  public void cancel() {
    myIsCancelled = true;
  }

  public boolean isCancelled() {
    return myIsCancelled;
  }

  @Override
  public void run() {
    myCallback.started(myValues.size());
    int proceedCount;
    for (proceedCount = 0; proceedCount < myValues.size() && !myIsCancelled; proceedCount++) {
      JavaReferenceInfo info = myValues.get(proceedCount);
      CheckingResult result = myChecker.check(info.getObjectReference());
      FilteringTaskCallback.Action action = switch (result.getResult()) {
        case MATCH -> myCallback.matched(info);
        case NO_MATCH -> myCallback.notMatched(info);
        case ERROR -> myCallback.error(info, result.getFailureDescription());
      };

      if (action == FilteringTaskCallback.Action.STOP) {
        break;
      }
    }

    FilteringResult reason = myIsCancelled
                             ? FilteringResult.INTERRUPTED
                             : proceedCount == myValues.size()
                               ? FilteringResult.ALL_CHECKED
                               : FilteringResult.LIMIT_REACHED;

    myCallback.completed(reason);
  }

  public static boolean isEmptyFilter(@NotNull XExpression expression) {
    return StringUtil.isEmptyOrSpaces(expression.getExpression());
  }

  public interface ValuesList {
    int size();

    JavaReferenceInfo get(int index);
  }
}
