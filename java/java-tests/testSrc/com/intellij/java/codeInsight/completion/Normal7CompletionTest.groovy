// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
/**
 * @author peter
 */
@CompileStatic
class Normal7CompletionTest extends NormalCompletionTestCase {
  final LightProjectDescriptor projectDescriptor = JAVA_1_7

  void testGenericInsideDiamond() { doTest() }


}
