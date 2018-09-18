/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.jsonSchema.schemaFile;

import com.jetbrains.jsonSchema.*;
import com.jetbrains.jsonSchema.fixes.JsonSchemaQuickFixTest;
import com.jetbrains.jsonSchema.impl.JsonBySchemaCompletionTest;
import com.jetbrains.jsonSchema.impl.JsonBySchemaHeavyCompletionTest;
import com.jetbrains.jsonSchema.impl.JsonSchemaReadTest;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author Irina.Chernushina on 4/12/2017.
 */
@SuppressWarnings({"JUnitTestCaseWithNoTests", "JUnitTestClassNamingConvention"})
public class JsonSchemaTestSuite extends TestCase {
  public static Test suite() {
    final TestSuite suite = new TestSuite(JsonSchemaTestSuite.class.getSimpleName());
    suite.addTestSuite(JsonSchemaCrossReferencesTest.class);
    suite.addTestSuite(JsonSchemaDocumentationTest.class);
    suite.addTestSuite(JsonSchemaHighlightingTest.class);
    suite.addTestSuite(JsonSchemaPatternComparatorTest.class);
    suite.addTestSuite(JsonSchemaSelfHighligthingTest.class);
    suite.addTestSuite(JsonBySchemaCompletionTest.class);
    suite.addTestSuite(JsonBySchemaHeavyCompletionTest.class);
    suite.addTestSuite(JsonSchemaReadTest.class);
    suite.addTestSuite(JsonSchemaFileResolveTest.class);
    suite.addTestSuite(JsonSchemaPerformanceTest.class);
    suite.addTestSuite(JsonSchemaQuickFixTest.class);
    return suite;
  }
}