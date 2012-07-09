/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.featureStatistics;

import java.util.Calendar;

/**
 * User: anna
 * Date: 7/5/12
 */
public class CumulativeStatistics {
  public int invocations = 0;
  public long startDate = 0;
  public int dayCount = 0;
  public long lastDate = 0;

  public void registerInvocation() {
    invocations++;

    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    long today = cal.getTimeInMillis();

    if (startDate == 0) {
      startDate = today;
    }
    if (lastDate == 0) {
      lastDate = today;
      dayCount = 1;
    }
    else if (today != lastDate) {
      lastDate = today;
      dayCount++;
    }
  }
  
}
