// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.schemaFile;

import com.jetbrains.jsonSchema.JsonSchemaPatternComparatorTest;
import com.jetbrains.jsonSchema.impl.JsonSchemaReadTest;
import junit.framework.Test;
import junit.framework.TestSuite;

@SuppressWarnings({"JUnitTestClassNamingConvention"})
public final class JsonSchemaTestSuite {
  public static Test suite() {
    final TestSuite suite = new TestSuite(JsonSchemaTestSuite.class.getSimpleName());
    suite.addTestSuite(JsonSchemaPatternComparatorTest.class);
    suite.addTestSuite(JsonSchemaReadTest.class);
    return suite;
  }
}