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
package com.intellij;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;

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

    TestClassesFilter classesFilter = GroupBasedTestClassFilter.createOn(getReader(filterText), Collections.singletonList("Group1"));
    assertTrue(classesFilter.matches("com.intellij.package1.Test", null));
    assertTrue(classesFilter.matches("com.intellij.package1.Test2", null));
    assertFalse(classesFilter.matches("com.intellij.package2.Test", null));
    assertTrue(classesFilter.matches("com.intellij.package2.ExcludedTest", null));
    assertTrue(classesFilter.matches("com.intellij.package3.package4", null));
    assertTrue(classesFilter.matches("com.intellij.package3.package5.package4", null));
    assertFalse(classesFilter.matches("com.intellij.package3", null));
    assertFalse(classesFilter.matches("com.intellij", null));
    assertFalse(classesFilter.matches("com.intellij.Test", null));
    assertFalse(classesFilter.matches("com.intellij.package5.Test", null));
    assertFalse(classesFilter.matches("com.intellij.package5.Test2", null));
    assertFalse(classesFilter.matches("com.intellij.package6.Test", null));
    assertFalse(classesFilter.matches("com.intellij.package6.ExcludedTest", null));
    assertFalse(classesFilter.matches("com.intellij.package7.package8", null));
    assertFalse(classesFilter.matches("com.intellij.package7.package5.package8", null));
    assertFalse(classesFilter.matches("com.intellij.package7", null));

    classesFilter = GroupBasedTestClassFilter.createOn(getReader(filterText), Collections.singletonList("Group2"));
    assertFalse(classesFilter.matches("com.intellij.package1.Test", null));
    assertFalse(classesFilter.matches("com.intellij.package1.Test2", null));
    assertFalse(classesFilter.matches("com.intellij.package2.Test", null));
    assertFalse(classesFilter.matches("com.intellij.package2.ExcludedTest", null));
    assertFalse(classesFilter.matches("com.intellij.package3.package4", null));
    assertFalse(classesFilter.matches("com.intellij.package3.package5.package4", null));
    assertFalse(classesFilter.matches("com.intellij.package3", null));
    assertFalse(classesFilter.matches("com.intellij", null));
    assertFalse(classesFilter.matches("com.intellij.Test", null));
    assertTrue(classesFilter.matches("com.intellij.package5.Test", null));
    assertTrue(classesFilter.matches("com.intellij.package5.Test2", null));
    assertFalse(classesFilter.matches("com.intellij.package6.Test", null));
    assertTrue(classesFilter.matches("com.intellij.package6.ExcludedTest", null));
    assertTrue(classesFilter.matches("com.intellij.package7.package8", null));
    assertTrue(classesFilter.matches("com.intellij.package7.package5.package8", null));
    assertFalse(classesFilter.matches("com.intellij.package7", null));

    classesFilter = GroupBasedTestClassFilter.createOn(getReader(filterText),
                                                       Collections.singletonList(GroupBasedTestClassFilter.ALL_EXCLUDE_DEFINED));
    checkForAllExcludedDefinedGroup(classesFilter);

    classesFilter = GroupBasedTestClassFilter.createOn(getReader(filterText), Collections.<String>emptyList());
    checkForAllExcludedDefinedGroup(classesFilter);

    classesFilter = GroupBasedTestClassFilter.createOn(getReader(filterText), Arrays.asList("Group1", "Group2"));
    assertTrue(classesFilter.matches("com.intellij.package1.Test", null));
    assertTrue(classesFilter.matches("com.intellij.package5.Test", null));
    assertFalse(classesFilter.matches("com.intellij.package4.Test", null));

    classesFilter = GroupBasedTestClassFilter.createOn(getReader(filterText), Arrays.asList("Group1", GroupBasedTestClassFilter.ALL_EXCLUDE_DEFINED));
    assertTrue(classesFilter.matches("com.intellij.package1.Test", null));
    assertFalse(classesFilter.matches("com.intellij.package5.Test", null));
    assertTrue(classesFilter.matches("com.intellij.package4.Test", null));
  }

  private static InputStreamReader getReader(String filterText) throws UnsupportedEncodingException {
    return new InputStreamReader(new ByteArrayInputStream(filterText.getBytes(CharsetToolkit.UTF8_CHARSET)));
  }

  private static void checkForAllExcludedDefinedGroup(TestClassesFilter classesFilter) {
    assertFalse(classesFilter.matches("com.intellij.package1.Test", null));
    assertFalse(classesFilter.matches("com.intellij.package1.Test2", null));
    assertTrue(classesFilter.matches("com.intellij.package2.Test", null));
    assertFalse(classesFilter.matches("com.intellij.package2.ExcludedTest", null));
    assertFalse(classesFilter.matches("com.intellij.package3.package4", null));
    assertFalse(classesFilter.matches("com.intellij.package3.package5.package4", null));
    assertTrue(classesFilter.matches("com.intellij.package3", null));
    assertTrue(classesFilter.matches("com.intellij", null));
    assertTrue(classesFilter.matches("com.intellij.Test", null));
    assertFalse(classesFilter.matches("com.intellij.package5.Test", null));
    assertFalse(classesFilter.matches("com.intellij.package5.Test2", null));
    assertTrue(classesFilter.matches("com.intellij.package6.Test", null));
    assertFalse(classesFilter.matches("com.intellij.package6.ExcludedTest", null));
    assertFalse(classesFilter.matches("com.intellij.package7.package8", null));
    assertFalse(classesFilter.matches("com.intellij.package7.package5.package8", null));
    assertTrue(classesFilter.matches("com.intellij.package7", null));
  }
}
