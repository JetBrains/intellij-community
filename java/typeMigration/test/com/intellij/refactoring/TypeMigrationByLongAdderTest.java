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
package com.intellij.refactoring;

import com.intellij.refactoring.typeMigration.rules.LongAdderConversionRule;

/**
 * @author Dmitry Batkovich
 */
public class TypeMigrationByLongAdderTest extends TypeMigrationTestBase {
  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/typeMigrationByLongAdder/";
  }

  public void testDirectCallsWithoutAssignment() {
    doTestFieldType("l",
                    getElementFactory().createTypeFromText(LongAdderConversionRule.JAVA_UTIL_CONCURRENT_ATOMIC_LONG_ADDER, null));
  }

  public void testIncrementDecrement() {
    doTestFieldType("l",
                    getElementFactory().createTypeFromText(LongAdderConversionRule.JAVA_UTIL_CONCURRENT_ATOMIC_LONG_ADDER, null));
  }

  public void testToPrimitivesAndString() {
    doTestFieldType("i",
                    getElementFactory().createTypeFromText(LongAdderConversionRule.JAVA_UTIL_CONCURRENT_ATOMIC_LONG_ADDER, null));
  }

  public void testUnconvertable() {
    doTestFieldType("i",
                    getElementFactory().createTypeFromText(LongAdderConversionRule.JAVA_UTIL_CONCURRENT_ATOMIC_LONG_ADDER, null));
  }
}
