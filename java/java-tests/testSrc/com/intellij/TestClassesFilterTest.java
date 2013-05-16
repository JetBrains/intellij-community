/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij;

import com.intellij.openapi.diagnostic.Logger;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestClassesFilterTest {
  private static final Logger LOG = Logger.getInstance("#com.intellij.TestClassesFilterTest");

  @Test
  public void test() throws Exception {
    LOG.info("test");

    String filterText = "[Group1]\n" +
                        "com.intellij.package1.*\n" +
                        "com.intellij.package2.ExcludedTest\n" +
                        "com.intellij.package3.*package4\n" +
                        "[Group2]\n" +
                        "com.intellij.package5.*\n" +
                        "com.intellij.package6.ExcludedTest\n" +
                        "com.intellij.package7.*package8";

    TestClassesFilter classesFilter = GroupBasedTestClassFilter.createOn(getReader(filterText), "Group1");
    assertTrue(classesFilter.matches("com.intellij.package1.Test"));
    assertTrue(classesFilter.matches("com.intellij.package1.Test2"));
    assertFalse(classesFilter.matches("com.intellij.package2.Test"));
    assertTrue(classesFilter.matches("com.intellij.package2.ExcludedTest"));
    assertTrue(classesFilter.matches("com.intellij.package3.package4"));
    assertTrue(classesFilter.matches("com.intellij.package3.package5.package4"));
    assertFalse(classesFilter.matches("com.intellij.package3"));
    assertFalse(classesFilter.matches("com.intellij"));
    assertFalse(classesFilter.matches("com.intellij.Test"));
    assertFalse(classesFilter.matches("com.intellij.package5.Test"));
    assertFalse(classesFilter.matches("com.intellij.package5.Test2"));
    assertFalse(classesFilter.matches("com.intellij.package6.Test"));
    assertFalse(classesFilter.matches("com.intellij.package6.ExcludedTest"));
    assertFalse(classesFilter.matches("com.intellij.package7.package8"));
    assertFalse(classesFilter.matches("com.intellij.package7.package5.package8"));
    assertFalse(classesFilter.matches("com.intellij.package7"));

    classesFilter = GroupBasedTestClassFilter.createOn(getReader(filterText), "Group2");
    assertFalse(classesFilter.matches("com.intellij.package1.Test"));
    assertFalse(classesFilter.matches("com.intellij.package1.Test2"));
    assertFalse(classesFilter.matches("com.intellij.package2.Test"));
    assertFalse(classesFilter.matches("com.intellij.package2.ExcludedTest"));
    assertFalse(classesFilter.matches("com.intellij.package3.package4"));
    assertFalse(classesFilter.matches("com.intellij.package3.package5.package4"));
    assertFalse(classesFilter.matches("com.intellij.package3"));
    assertFalse(classesFilter.matches("com.intellij"));
    assertFalse(classesFilter.matches("com.intellij.Test"));
    assertTrue(classesFilter.matches("com.intellij.package5.Test"));
    assertTrue(classesFilter.matches("com.intellij.package5.Test2"));
    assertFalse(classesFilter.matches("com.intellij.package6.Test"));
    assertTrue(classesFilter.matches("com.intellij.package6.ExcludedTest"));
    assertTrue(classesFilter.matches("com.intellij.package7.package8"));
    assertTrue(classesFilter.matches("com.intellij.package7.package5.package8"));
    assertFalse(classesFilter.matches("com.intellij.package7"));

    classesFilter = GroupBasedTestClassFilter.createOn(getReader(filterText), null);
    checkForNullGroup(classesFilter);

    classesFilter = GroupBasedTestClassFilter.createOn(getReader(filterText), GroupBasedTestClassFilter.ALL_EXCLUDE_DEFINED);
    checkForNullGroup(classesFilter);
  }

  private static InputStreamReader getReader(String filterText) throws UnsupportedEncodingException {
    return new InputStreamReader(new ByteArrayInputStream(filterText.getBytes("UTF-8")));
  }

  private static void checkForNullGroup(TestClassesFilter classesFilter) {
    assertFalse(classesFilter.matches("com.intellij.package1.Test"));
    assertFalse(classesFilter.matches("com.intellij.package1.Test2"));
    assertTrue(classesFilter.matches("com.intellij.package2.Test"));
    assertFalse(classesFilter.matches("com.intellij.package2.ExcludedTest"));
    assertFalse(classesFilter.matches("com.intellij.package3.package4"));
    assertFalse(classesFilter.matches("com.intellij.package3.package5.package4"));
    assertTrue(classesFilter.matches("com.intellij.package3"));
    assertTrue(classesFilter.matches("com.intellij"));
    assertTrue(classesFilter.matches("com.intellij.Test"));
    assertFalse(classesFilter.matches("com.intellij.package5.Test"));
    assertFalse(classesFilter.matches("com.intellij.package5.Test2"));
    assertTrue(classesFilter.matches("com.intellij.package6.Test"));
    assertFalse(classesFilter.matches("com.intellij.package6.ExcludedTest"));
    assertFalse(classesFilter.matches("com.intellij.package7.package8"));
    assertFalse(classesFilter.matches("com.intellij.package7.package5.package8"));
    assertTrue(classesFilter.matches("com.intellij.package7"));
  }
}
