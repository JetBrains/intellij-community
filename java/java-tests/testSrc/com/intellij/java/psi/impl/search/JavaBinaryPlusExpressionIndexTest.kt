// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.impl.search

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.impl.java.JavaBinaryPlusExpressionIndex
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileContentImpl
import org.intellij.lang.annotations.Language

class JavaBinaryPlusExpressionIndexTest : BasePlatformTestCase() {
  fun testIndex() {
    @Language("JAVA")
    val file = myFixture.configureByText(JavaFileType.INSTANCE, """
            package org.some;

            class Xyz {

                void someMethod(Long o, String o2) {
                  String s = "qwe" + "asd";
                  String s1 = "qwe" + o;
                  String s2 = "qwe" + o2 + "xxx";
                  String x = "uuu" + o + 
                              /*"  <inspection_tool class=\"ManifestDomInspection\" enabled=\"true\" level=\"ERROR\" enabled_by_default=\"false\" />\n" +*/
                             "asd";
                }
            }
    """).virtualFile
    val content = FileContentImpl.createByFile(file, project)
    val data = JavaBinaryPlusExpressionIndex().indexer.map(content).entries.first().value.offsets!!

    assertEquals(5, data.size)
    assertEquals(190, data[0])
    assertEquals(231, data[1])
    assertEquals(236, data[2])
    assertEquals(280, data[3])
    assertEquals(284, data[4])
  }
}
