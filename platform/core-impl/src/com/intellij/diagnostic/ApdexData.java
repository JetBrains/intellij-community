/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diagnostic;

/**
 * Collects performance data as in Apdex: http://www.apdex.org/overview.html.
 * Prints this raw data instead of Apdex index, because the index loses too much valuable information: http://www.coscale.com/blog/web-application-performance-what-apdex-doesnt-tell-you.
 * @author peter
 */
class ApdexData {
  public static final ApdexData EMPTY = new ApdexData(0, 0, 0);
  private final long myTotalCount;
  private final long mySatisfiedCount;
  private final long mySluggishCount;

  private ApdexData(long totalCount, long satisfiedCount, long sluggishCount) {
    assert totalCount >= 0;
    assert satisfiedCount >= 0;
    assert sluggishCount >= 0;

    assert satisfiedCount + sluggishCount <= totalCount;

    myTotalCount = totalCount;
    mySatisfiedCount = satisfiedCount;
    mySluggishCount = sluggishCount;
  }

  ApdexData withEvent(long requiredTime, long actualTime) {
    boolean satisfied = actualTime < requiredTime;
    boolean tolerable = !satisfied && actualTime < requiredTime * 4;
    return new ApdexData(myTotalCount + 1, mySatisfiedCount + (satisfied ? 1 : 0), mySluggishCount + (tolerable ? 1 : 0));
  }

  private long getSlowCount() {
    return myTotalCount - mySluggishCount - mySatisfiedCount;
  }

  String summarizePerformanceSince(ApdexData since) {
    long total = myTotalCount - since.myTotalCount;
    long sluggish = mySluggishCount - since.mySluggishCount;
    long slow = getSlowCount() - since.getSlowCount();

    if (sluggish == 0 && slow == 0) return "ok";

    if (slow == 0) return sluggish + "/" + total + " sluggish";

    return sluggish + "/" + total + " sluggish, " + slow + "/" + total + " very slow";
  }

}

