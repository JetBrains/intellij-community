// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.impl.search

import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.psi.impl.java.JavaBinaryPlusExpressionIndex
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.indexing.IndexingDataKeys
import org.intellij.lang.annotations.Language

class JavaBinaryPlusExpressionIndexTest : LightPlatformCodeInsightFixtureTestCase() {
  fun testIndex() {
    @Language("JAVA")
    val file = myFixture.configureByText(StdFileTypes.JAVA, """
            package org.some;

            class Xyz {

                void someMethod(Long o, String o2) {
                  String s = "qwe" + "asd";
                  String s1 = "qwe" + o;
                  String s2 = "qwe" + o2 + "xxx";
                }
            }
    """).virtualFile
    val content = FileContentImpl.createByFile(file)
    content.putUserData(IndexingDataKeys.PROJECT, project)
    val data = JavaBinaryPlusExpressionIndex().indexer.map(content).entries.first().value.offsets!!

    assertEquals(3, data.size)
    assertEquals(190, data[0])
    assertEquals(231, data[1])
    assertEquals(236, data[2])
  }
}
