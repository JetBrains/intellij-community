// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic;

import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.indexing.IndexId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.intellij.util.indexing.diagnostic.IndexOperationFusCollector.*;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

/**
 * Tests for [IndexOperationFusCollector].
 */
@RunWith(JUnit4.class)
public class IndexOperationFusCollectorTest extends JavaCodeInsightFixtureTestCase {

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
  public void reportingDontThrowAnythingIfStartedCalledTwice_without_THROW_ON_INCORRECT_USAGE() {
    assumeFalse("Check only if !THROW_ON_INCORRECT_USAGE",
                THROW_ON_INCORRECT_USAGE);
    //check for 'allKeys' only because all them have same superclass
    var trace = lookupAllKeysStarted(INDEX_ID);
    trace.lookupStarted(INDEX_ID);
  }

  @Test
  public void reportingDontThrowAnythingIfReportingMethodsCalledWithoutStartedFirst_without_THROW_ON_INCORRECT_USAGE() {
    assumeFalse("Check only if !THROW_ON_INCORRECT_USAGE",
                THROW_ON_INCORRECT_USAGE);
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
  public void reportingThrowExceptionIfStartedCalledTwice_with_THROW_ON_INCORRECT_USAGE() {
    assumeTrue("Check only if THROW_ON_INCORRECT_USAGE",
               THROW_ON_INCORRECT_USAGE);
    //check for 'allKeys' only because all them have same superclass
    var trace = lookupAllKeysStarted(INDEX_ID);
    try {
      trace.lookupStarted(INDEX_ID);
      fail("Subsequent .started() without finish should throw exception if THROW_ON_INCORRECT_USAGE=true");
    }
    catch (AssertionError e) {
      trace.lookupFinished();//without finishing -> legit tests run after will start to fail
    }
  }

  @Test
  public void lookupFinishedThrowExceptionIfCalledWithoutStartedFirst_with_THROW_ON_INCORRECT_USAGE() {
    assumeTrue("Check only if THROW_ON_INCORRECT_USAGE",
               THROW_ON_INCORRECT_USAGE);
    //check for 'allKeys' only because all them have same superclass
    var trace = TRACE_OF_ALL_KEYS_LOOKUP.get();
    try {
      trace.lookupFinished();
      fail(".started() must be called throw exception if THROW_ON_INCORRECT_USAGE=true");
    }
    catch (AssertionError e) {

    }
  }
}
