/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.diff

import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.UsefulTestCase
import junit.framework.ComparisonFailure
import junit.framework.TestCase

fun assertTrue(actual: Boolean, message: String = "") {
  TestCase.assertTrue(message, actual)
}

fun assertFalse(actual: Boolean, message: String = "") {
  TestCase.assertFalse(message, actual)
}

fun assertEquals(expected: Any?, actual: Any?, message: String = "") {
  TestCase.assertEquals(message, expected, actual)
}

fun assertEquals(expected: CharSequence?, actual: CharSequence?, message: String = "") {
  if (!StringUtil.equals(expected, actual)) throw ComparisonFailure(message, expected?.toString(), actual?.toString())
}

fun assertEmpty(collection: Collection<*>, message: String = "") {
  UsefulTestCase.assertEmpty(message, collection)
}

fun assertOrderedEquals(expected: Collection<*>, actual: Collection<*>, message: String = "") {
  UsefulTestCase.assertOrderedEquals(message, actual, expected)
}

fun assertNull(actual: Any?, message: String = "") {
  TestCase.assertNull(message, actual)
}

fun assertNotNull(actual: Any?, message: String = "") {
  TestCase.assertNotNull(message, actual)
}
