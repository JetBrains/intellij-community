// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic;

import com.intellij.testFramework.LightPlatform4TestCase;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.diagnostic.IndexOperationFUS.IndexOperationAggregatesCollector;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;

import static com.intellij.util.indexing.diagnostic.IndexOperationFUS.IndexOperationFusCollector.*;
import static com.intellij.util.indexing.diagnostic.IndexOperationFUS.MAX_LOOKUP_DEPTH;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

/**
 * Tests for {@linkplain IndexOperationFUS.IndexOperationFusCollector}
 */
public class IndexOperationFusCollectorTest extends LightPlatform4TestCase/*JavaCodeInsightFixtureTestCase*/ {

  //This class mostly checks methods contracts. It would be nice to check reported events also, but there
  //     is no simple way to do it (so it seems)

  private static final IndexId<?, ?> INDEX_ID = IndexId.create("test-index");

  @Test
  public void stubEntriesLookupReportingDontThrowAnythingIfCalledInCorrectSequence() {
    var trace = lookupStubEntriesStarted(INDEX_ID);
    try (trace) {
      trace.withProject(null);
      trace.indexValidationFinished();
      trace.stubTreesDeserializingStarted();
      trace.totalKeysIndexed(10);
      trace.lookupFailed();
    }
  }

  @Test
  public void fileEntriesLookupReportingDontThrowAnythingIfCalledInCorrectSequence() {
    var trace = lookupEntriesStarted(INDEX_ID);
    try (trace) {
      trace.withProject(null);
      trace.indexValidationFinished();
      trace.totalKeysIndexed(10);
      trace.lookupFailed();
    }
  }

  @Test
  public void allKeysLookupReportingDontThrowAnythingIfCalledInCorrectSequence() {
    var trace = lookupAllKeysStarted(INDEX_ID);
    try (trace) {
      trace.withProject(null);
      trace.indexValidationFinished();
      trace.totalKeysIndexed(10);
      trace.lookupFailed();
    }
  }


  @Test
  public void reportingCouldBeReEnteredUpToMaxDepthAndDifferentTracesProvidedForEachReEntrantCall() {
    //check for 'allKeys' only, because all them have same superclass
    final Deque<LookupAllKeysTrace> tracesStack = new ArrayDeque<>();
    try {
      for (int i = 0; i < MAX_LOOKUP_DEPTH; i++) {
        final LookupAllKeysTrace trace = lookupAllKeysStarted(INDEX_ID);
        if (!tracesStack.isEmpty()) {
          assertNotSame(
            "Each lookupStarted() call should return unique Trace instance",
            tracesStack.peek(),
            trace
          );
        }
        tracesStack.push(trace);
      }
      assertEquals(
        "All traces up to MAX_DEPTH must be different",
        new HashSet<>(tracesStack).size(),
        MAX_LOOKUP_DEPTH
      );
    }
    finally {//unwind stack back to not affect other tests:
      for (int i = 0; i < MAX_LOOKUP_DEPTH; i++) {
        TRACE_OF_ALL_KEYS_LOOKUP.get().lookupFinished();
      }
    }
  }

  @Test
  public void reportingCouldBeReEnteredIfNotMoreThanMaxDepth() {
    //check for 'allKeys' only because all them have same superclass
    try {
      for (int i = 0; i < MAX_LOOKUP_DEPTH; i++) {
        lookupAllKeysStarted(INDEX_ID);
      }
    }
    finally {//unwind stack back to not affect other tests:
      for (int i = 0; i < MAX_LOOKUP_DEPTH; i++) {
        TRACE_OF_ALL_KEYS_LOOKUP.get().lookupFinished();
      }
    }
  }

  @Test
  public void reportingDontThrowExceptionIfStartedCalledMoreThanMaxDepth_without_THROW_ON_INCORRECT_USAGE() {
    assumeFalse("Check only if !THROW_ON_INCORRECT_USAGE",
                IndexOperationFUS.THROW_ON_INCORRECT_USAGE);
    //check for 'allKeys' only because all them have same superclass
    try {
      for (int i = 0; i <= MAX_LOOKUP_DEPTH; i++) {
        lookupAllKeysStarted(INDEX_ID);
      }
    }
    finally {//unwind stack back to not affect other tests:
      for (int i = 0; i <= MAX_LOOKUP_DEPTH; i++) {
        TRACE_OF_ALL_KEYS_LOOKUP.get().lookupFinished();
      }
    }
  }

  @Test
  public void reportingDontThrowAnythingIfReportingMethodsCalledWithoutStartedFirst_without_THROW_ON_INCORRECT_USAGE() {
    assumeFalse("Check only if !THROW_ON_INCORRECT_USAGE",
                IndexOperationFUS.THROW_ON_INCORRECT_USAGE);
    //check for 'allKeys' only because all them have same superclass
    try (var trace = TRACE_OF_ALL_KEYS_LOOKUP.get()) {
      trace.withProject(null);
      trace.withProject(null);
      trace.indexValidationFinished();
      trace.totalKeysIndexed(10);
      trace.lookupFailed();
      trace.lookupFinished();
    }
  }

  @Test
  public void lookupFinishedThrowExceptionIfCalledWithoutStartedFirst_with_THROW_ON_INCORRECT_USAGE() {
    assumeTrue("Check only if THROW_ON_INCORRECT_USAGE",
               IndexOperationFUS.THROW_ON_INCORRECT_USAGE);
    //check for 'allKeys' only because all them have same superclass
    var trace = TRACE_OF_ALL_KEYS_LOOKUP.get();
    boolean finishWithoutStartFails;
    try {
      trace.lookupFinished();
      finishWithoutStartFails = false;
    }
    catch (AssertionError e) {
      finishWithoutStartFails = true;
    }

    assumeTrue(
      ".started() must be called throw exception if THROW_ON_INCORRECT_USAGE=true",
      finishWithoutStartFails
    );
  }

  /** Checks IDEA-300630 */
  @Test
  public void bigValuesReportingDoesntThrowExceptions() {
    IndexOperationAggregatesCollector.recordAllKeysLookup(
      INDEX_ID, false, IndexOperationAggregatesCollector.MAX_TRACKABLE_DURATION_MS + 1
    );
  }

  /** Checks IDEA-300630 */
  @Test
  public void negativeValuesReportingDoesntThrowExceptions() {
    IndexOperationAggregatesCollector.recordAllKeysLookup(
      INDEX_ID, false, -1
    );
  }

  @After
  public void checkTestCleansAfterItself() {
    final LookupTraceBase<?>[] traces = {
      TRACE_OF_ALL_KEYS_LOOKUP.get(),
      TRACE_OF_ENTRIES_LOOKUP.get(),
      TRACE_OF_STUB_ENTRIES_LOOKUP.get()
    };
    for (LookupTraceBase<?> trace : traces) {
      if (trace.traceWasStarted()) {
        final String message = "All traces must be returned to un-initialized state after test: " + trace;
        //now fix it, so next tests are not affected
        while (trace.traceWasStarted()) {
          trace.lookupFinished();
        }
        //fail: i.e. assume test must clean for itself, and lack of cleanup is a test bug
        fail(message);
      }
    }
  }


}
