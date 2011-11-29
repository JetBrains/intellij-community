/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij;

import com.intellij.openapi.diagnostic.Logger;
import junit.framework.TestCase;
import org.jetbrains.annotations.Nullable;

import java.io.*;

public class TestClassesFilterTest extends TestCase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.TestClassesFilterTest");

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  public void test() throws IOException {

    LOG.info("test");

    String filterText = "[Group1]\n" +
                        "com.intellij.package1.*\n" +
                        "com.intellij.package2.ExcludedTest\n" +
                        "com.intellij.package3.*package4\n" +
                        "[Group2]\n" +
                        "com.intellij.package5.*\n" +
                        "com.intellij.package6.ExcludedTest\n" +
                        "com.intellij.package7.*package8";


    TestClassesFilter classesFilter = GroupBasedTestClassFilter
      .createOn("Group1", new InputStreamReader(new ByteArrayInputStream(filterText.getBytes())));
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

    classesFilter = GroupBasedTestClassFilter
      .createOn("Group2", new InputStreamReader(new ByteArrayInputStream(filterText.getBytes())));
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

    checkForNullGroup(filterText, null);
    checkForNullGroup(filterText, GroupBasedTestClassFilter.ALL_EXCLUDE_DEFINED);

  }

  public void testTwoFiles() throws Exception {
    TestClassesFilter filter = GroupBasedTestClassFilter.createOn("GROUP2",
                                                                  new StringReader("[GROUP1]\n" +
                                                                                   "foo.*\n" +
                                                                                   "bar.*\n" +
                                                                                   "[GROUP2]\n" +
                                                                                   "foo.bar.*"),
                                                                  new StringReader("[GROUP2]\n" +
                                                                                   "bar.foo.*"));
    assertFalse(filter.matches("test.Test"));
    assertTrue(filter.matches("foo.bar.Test"));
    assertTrue(filter.matches("bar.foo.Test"));
  }

  private static void checkForNullGroup(String filterText, @Nullable String group0Name) {
    TestClassesFilter classesFilter = GroupBasedTestClassFilter.createOn(group0Name,
                                                                         new StringReader(filterText));

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
