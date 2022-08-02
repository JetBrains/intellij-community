// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.intellij.util.indexing.diagnostic.IndexOperationFusStatisticsCollector.*;
import static org.junit.Assume.assumeFalse;

/**
 * Tests for [IndexOperationFusStatisticsCollector].
 */
@RunWith(JUnit4.class)
public class IndexOperationFusStatisticsCollectorTest extends JavaCodeInsightFixtureTestCase {

  //This class mostly checks methods contracts. It would be nice to check reported events also, but there
  //     is no simple way to do it (so it seems)

  private static final IndexId<?, ?> INDEX_ID = IndexId.create("test-index");

  @Test
  public void stubValuesLookupReportingDontThrowAnythingIfCalledInCorrectSequence() {
    var trace = stubValuesLookupStarted(INDEX_ID);
    try (trace) {
      trace.withProject(null);
      trace.indexValidationFinished();
      trace.stubTreesDeserializingStarted();
      trace.totalKeysIndexed(10);
      trace.lookupFailed();
    }
  }

  @Test
  public void fileValuesLookupReportingDontThrowAnythingIfCalledInCorrectSequence() {
    var trace = valuesLookupStarted(INDEX_ID);
    try (trace) {
      trace.withProject(null);
      trace.indexValidationFinished();
      trace.totalKeysIndexed(10);
      trace.lookupFailed();
    }
  }

  @Test
  public void allKeysLookupReportingDontThrowAnythingIfCalledInCorrectSequence() {
    var trace = allKeysLookupStarted(INDEX_ID);
    try (trace) {
      trace.withProject(null);
      trace.indexValidationFinished();
      trace.totalKeysIndexed(10);
      trace.lookupFailed();
    }
  }


  @Test
  public void reportingDontThrowAnythingIfStartedCalledTwice() {
    assumeFalse("Check only if !THROW_ON_INCORRECT_USAGE",
                THROW_ON_INCORRECT_USAGE);
    //check for 'allKeys' only because all them have same superclass
    var trace = allKeysLookupStarted(INDEX_ID);
    trace.lookupStarted(INDEX_ID);
  }

  @Test
  public void reportingDontThrowAnythingIfReportingMethodsCalledWithoutStartedFirst() {
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
}
