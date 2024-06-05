// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.schemaFile;

import com.jetbrains.jsonSchema.*;
import com.jetbrains.jsonSchema.fixes.JsonSchemaQuickFixTest;
import com.jetbrains.jsonSchema.impl.*;
import junit.framework.Test;
import junit.framework.TestSuite;

@SuppressWarnings({"JUnitTestClassNamingConvention"})
public final class JsonSchemaTestSuite {
  public static Test suite() {
    final TestSuite suite = new TestSuite(JsonSchemaTestSuite.class.getSimpleName());
    suite.addTestSuite(JsonSchemaCrossReferencesTest.class);
    suite.addTestSuite(JsonSchemaDocumentationTest.class);
    suite.addTestSuite(JsonSchemaHighlightingTest.class);
    suite.addTestSuite(JsonSchemaInjectionTest.class);
    suite.addTestSuite(JsonSchemaReSharperHighlightingTest.class);
    suite.addTestSuite(JsonSchemaPatternComparatorTest.class);
    suite.addTestSuite(JsonSchemaSelfHighlightingTest.class);
    suite.addTestSuite(JsonBySchemaCompletionTest.class);
    suite.addTestSuite(JsonBySchemaHeavyCompletionTest.class);
    suite.addTestSuite(JsonBySchemaNestedCompletionTest.class);
    suite.addTestSuite(JsonBySchemaHeavyNestedCompletionTest.class);
    suite.addTestSuite(JsonSchemaReadTest.class);
    suite.addTestSuite(JsonSchemaFileResolveTest.class);
    suite.addTestSuite(JsonSchemaPerformanceTest.class);
    suite.addTestSuite(JsonSchemaQuickFixTest.class);
    return suite;
  }
}