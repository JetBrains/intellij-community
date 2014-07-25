/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 3/26/14
 */
public class LocationAwareExternalSystemException extends ExternalSystemException {
  private final String myFilePath;
  private final Integer myLine;
  private final Integer myColumn;

  public LocationAwareExternalSystemException(@Nullable String message, String filePath, @NotNull String... quickFixes) {
    this(message, null, filePath, -1, -1, quickFixes);
  }

  public LocationAwareExternalSystemException(@Nullable String message, String filePath, Integer line, @NotNull String... quickFixes) {
    this(message, null, filePath, line, -1, quickFixes);
  }

  public LocationAwareExternalSystemException(@Nullable String message, String filePath, Integer line, Integer column, @NotNull String... quickFixes) {
    this(message, null, filePath, line, column, quickFixes);
  }

  public LocationAwareExternalSystemException(@Nullable Throwable cause, String filePath, Integer line, Integer column, @NotNull String... quickFixes) {
    this(null, cause, filePath, line, column, quickFixes);
  }

  public LocationAwareExternalSystemException(@Nullable String message,
                                              @Nullable Throwable cause,
                                              String filePath,
                                              Integer line,
                                              Integer column,
                                              @NotNull String... quickFixes) {
    super(message, cause, quickFixes);
    myFilePath = filePath;
    myLine = line;
    myColumn = column;
  }

  public String getFilePath() {
    return myFilePath;
  }

  public Integer getLine() {
    return myLine;
  }

  public Integer getColumn() {
    return myColumn;
  }
}
