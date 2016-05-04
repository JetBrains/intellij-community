/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An open-ended range of build numbers.
 */
public class BuildRange {
  private final BuildNumber since;
  private final BuildNumber until;

  public BuildRange(@NotNull BuildNumber since, @NotNull BuildNumber until) {
    this.since = since;
    this.until = until;
    if (since.compareTo(until) > 0) {
      throw new IllegalArgumentException("Invalid range: [" + since + "; " + until + "]");
    }
  }

  public boolean inRange(@NotNull BuildNumber build) {
    return since.compareTo(build) <= 0 && build.compareTo(until) <= 0;
  }

  @Contract("null, _ -> null; _, null -> null")
  public static BuildRange fromStrings(@Nullable String sinceVal, @Nullable String untilVal) {
    BuildNumber since = BuildNumber.fromString(sinceVal);
    BuildNumber until = BuildNumber.fromString(untilVal);
    return since != null && until != null ? new BuildRange(since, until) : null;
  }
}