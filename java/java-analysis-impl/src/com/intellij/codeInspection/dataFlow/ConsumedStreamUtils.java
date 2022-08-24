// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.psi.PsiMethod;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM;
import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;

public final class ConsumedStreamUtils {

  private static final CallMatcher ON_CLOSE_STREAM = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "onClose");

  private static final CallMatcher CLOSE_STREAM_MATCHERS = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "close");

  private static final CallMatcher SKIP_STREAM = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "parallel", "sequential");
  private static final CallMatcher MARK_AND_CONSUMED_STREAM =
    instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "iterator", "spliterator",
                 "forEach", "forEachOrdered", "toArray", "reduce", "collect", "sum", "min", "max", "count", "average", "summaryStatistics",
                 "anyMatch", "allMatch", "nonMatch", "findFirst", "findAny", "toList", "filter", "map", "mapToObj", "mapToInt", "mapToLong",
                 "mapToDouble", "flatMap", "flatMapToInt", "flatMapToLong", "flatMapToDouble", "mapMulti", "mapMultiToInt",
                 "mapMultiToLong", "mapMultiToDouble", "distinct", "sorted", "peek", "limit", "skip", "takeWhile", "dropWhile",
                 "asLongStream", "asDoubleStream", "boxed");
  private static final CallMatcher CALL_TO_MARK_CONSUMED =
    anyOf(MARK_AND_CONSUMED_STREAM, CLOSE_STREAM_MATCHERS);
  private static final CallMatcher NONE_LEAKS_STREAM =
    anyOf(MARK_AND_CONSUMED_STREAM, CLOSE_STREAM_MATCHERS, ON_CLOSE_STREAM, SKIP_STREAM);

  private static final CallMapper<Boolean> IS_CHECKED_CALL = new CallMapper<Boolean>()
    .register(MARK_AND_CONSUMED_STREAM, Boolean.TRUE)
    .register(ON_CLOSE_STREAM, Boolean.TRUE);

  public static boolean isCheckedCallForConsumedStream(PsiMethod method) {
    return IS_CHECKED_CALL.mapFirst(method) == Boolean.TRUE;
  }

  public static CallMatcher getSkipStreamMatchers() {
    return SKIP_STREAM;
  }

  public static CallMatcher getCallToMarkConsumedStreamMatchers() {
    return CALL_TO_MARK_CONSUMED;
  }

  public static CallMatcher getAllNonLeakStreamMatchers() {
    return NONE_LEAKS_STREAM;
  }
}
