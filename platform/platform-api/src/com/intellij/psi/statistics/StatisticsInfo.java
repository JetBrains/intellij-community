/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.statistics;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class StatisticsInfo implements Comparable<StatisticsInfo>{
  public static final StatisticsInfo EMPTY = new StatisticsInfo("empty", "empty");

  private static final StatisticsManager ourManager = StatisticsManager.getInstance();
  private final String myContext;
  private final String myValue;

  public StatisticsInfo(@NonNls @NotNull final String context, @NonNls @NotNull final String value) {
    myContext = context;
    myValue = value;
  }

  @NotNull
  public String getContext() {
    return myContext;
  }

  @NotNull
  public String getValue() {
    return myValue;
  }

  public int compareTo(final StatisticsInfo o) {
    return getUseCount() - o.getUseCount();
  }

  public void incUseCount() {
    ourManager.incUseCount(this);
  }

  public int getUseCount() {
    return ourManager.getUseCount(this);
  }

  public String toString() {
    return myContext + "::::" + myValue;
  }
}
