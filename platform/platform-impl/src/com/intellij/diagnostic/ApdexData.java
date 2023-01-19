// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

/**
 * Collects performance data as in Apdex: http://www.apdex.org/overview.html.
 * Prints this raw data instead of Apdex index, because the index loses too much valuable information: http://www.coscale.com/blog/web-application-performance-what-apdex-doesnt-tell-you.
 */
final class ApdexData {
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
    boolean sluggish = !satisfied && actualTime < requiredTime * 4;
    return new ApdexData(myTotalCount + 1, mySatisfiedCount + (satisfied ? 1 : 0), mySluggishCount + (sluggish ? 1 : 0));
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

