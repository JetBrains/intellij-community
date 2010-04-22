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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

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

    TestClassesFilter classesFilter = TestClassesFilter.createOn(new InputStreamReader(new ByteArrayInputStream(filterText.getBytes())));

    String group1Name = "Group1";
    assertTrue(classesFilter.matches("com.intellij.package1.Test", group1Name));
    assertTrue(classesFilter.matches("com.intellij.package1.Test2", group1Name));
    assertFalse(classesFilter.matches("com.intellij.package2.Test", group1Name));
    assertTrue(classesFilter.matches("com.intellij.package2.ExcludedTest", group1Name));
    assertTrue(classesFilter.matches("com.intellij.package3.package4", group1Name));
    assertTrue(classesFilter.matches("com.intellij.package3.package5.package4", group1Name));
    assertFalse(classesFilter.matches("com.intellij.package3", group1Name));
    assertFalse(classesFilter.matches("com.intellij", group1Name));
    assertFalse(classesFilter.matches("com.intellij.Test", group1Name));

    assertFalse(classesFilter.matches("com.intellij.package5.Test", group1Name));
    assertFalse(classesFilter.matches("com.intellij.package5.Test2", group1Name));
    assertFalse(classesFilter.matches("com.intellij.package6.Test", group1Name));
    assertFalse(classesFilter.matches("com.intellij.package6.ExcludedTest", group1Name));
    assertFalse(classesFilter.matches("com.intellij.package7.package8", group1Name));
    assertFalse(classesFilter.matches("com.intellij.package7.package5.package8", group1Name));
    assertFalse(classesFilter.matches("com.intellij.package7", group1Name));

    String group2Name = "Group2";
    assertFalse(classesFilter.matches("com.intellij.package1.Test", group2Name));
    assertFalse(classesFilter.matches("com.intellij.package1.Test2", group2Name));
    assertFalse(classesFilter.matches("com.intellij.package2.Test", group2Name));
    assertFalse(classesFilter.matches("com.intellij.package2.ExcludedTest", group2Name));
    assertFalse(classesFilter.matches("com.intellij.package3.package4", group2Name));
    assertFalse(classesFilter.matches("com.intellij.package3.package5.package4", group2Name));
    assertFalse(classesFilter.matches("com.intellij.package3", group2Name));
    assertFalse(classesFilter.matches("com.intellij", group2Name));
    assertFalse(classesFilter.matches("com.intellij.Test", group2Name));

    assertTrue(classesFilter.matches("com.intellij.package5.Test", group2Name));
    assertTrue(classesFilter.matches("com.intellij.package5.Test2", group2Name));
    assertFalse(classesFilter.matches("com.intellij.package6.Test", group2Name));
    assertTrue(classesFilter.matches("com.intellij.package6.ExcludedTest", group2Name));
    assertTrue(classesFilter.matches("com.intellij.package7.package8", group2Name));
    assertTrue(classesFilter.matches("com.intellij.package7.package5.package8", group2Name));
    assertFalse(classesFilter.matches("com.intellij.package7", group2Name));

    checkForNullGroup(classesFilter, null);
    checkForNullGroup(classesFilter, TestClassesFilter.ALL_EXCLUDE_DEFINED);

  }

  private static void checkForNullGroup(TestClassesFilter classesFilter, String group0Name) {
    assertFalse(classesFilter.matches("com.intellij.package1.Test", group0Name));
    assertFalse(classesFilter.matches("com.intellij.package1.Test2", group0Name));
    assertTrue(classesFilter.matches("com.intellij.package2.Test", group0Name));
    assertFalse(classesFilter.matches("com.intellij.package2.ExcludedTest", group0Name));
    assertFalse(classesFilter.matches("com.intellij.package3.package4", group0Name));
    assertFalse(classesFilter.matches("com.intellij.package3.package5.package4", group0Name));
    assertTrue(classesFilter.matches("com.intellij.package3", group0Name));
    assertTrue(classesFilter.matches("com.intellij", group0Name));
    assertTrue(classesFilter.matches("com.intellij.Test", group0Name));

    assertFalse(classesFilter.matches("com.intellij.package5.Test", group0Name));
    assertFalse(classesFilter.matches("com.intellij.package5.Test2", group0Name));
    assertTrue(classesFilter.matches("com.intellij.package6.Test", group0Name));
    assertFalse(classesFilter.matches("com.intellij.package6.ExcludedTest", group0Name));
    assertFalse(classesFilter.matches("com.intellij.package7.package8", group0Name));
    assertFalse(classesFilter.matches("com.intellij.package7.package5.package8", group0Name));
    assertTrue(classesFilter.matches("com.intellij.package7", group0Name));
  }
}
