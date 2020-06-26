// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.filtering;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public final class CheckingResultImpl implements CheckingResult {
  public static final CheckingResult SUCCESS = new CheckingResultImpl(CheckingResultImpl.Result.MATCH, "");
  public static final CheckingResult FAIL = new CheckingResultImpl(CheckingResultImpl.Result.NO_MATCH, "");

  private final Result myResult;
  private final String myDescription;

  private CheckingResultImpl(@NotNull Result result, @NotNull String description) {
    myResult = result;
    myDescription = description;
  }

  public static CheckingResult error(@NotNull String description) {
    return new CheckingResultImpl(Result.ERROR, description);
  }

  @Override
  @NotNull
  public Result getResult() {
    return myResult;
  }

  @Override
  @NotNull
  public String getFailureDescription() {
    return myDescription;
  }

  @Override
  public String toString() {
    if (StringUtil.isEmpty(myDescription)) {
      return myResult.toString();
    }
    return  myResult + ": " + myDescription;
  }
}
