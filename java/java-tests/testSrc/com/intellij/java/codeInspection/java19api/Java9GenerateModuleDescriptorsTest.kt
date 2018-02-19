// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.java19api

import com.intellij.codeInspection.java19api.Java9GenerateModuleDescriptorsAction
import junit.framework.TestCase

/**
 * @author Pavel.Dolgov
 */
class Java9GenerateModuleDescriptorsTest : TestCase() {

  fun testNameConverter() {
    doTestName("foo", ".foo")
    doTestName("foo", "foo.")
    doTestName("foo", ".foo")
    doTestName("foo", ".foo.")
    doTestName("foo.bar", "foo.bar")
    doTestName("foo.bar", ".foo.bar...")

    doTestName("zero", "0")
    doTestName("one.foo", "1.foo")
    doTestName("foo.two", "foo.2")
    doTestName("foo.three.bar", "foo.3.bar")
    doTestName("foo.fourfive.bar", "foo.45.bar")
    doTestName("foo.six.seven.bar", "foo.6.7.bar")
    doTestName("eight.foo.bar.nine", "8.foo.bar.9")
  }

  private fun doTestName(expected:String, name:String) {
    assertEquals(expected, Java9GenerateModuleDescriptorsAction.NameConverter.convertModuleName(name))
  }
}