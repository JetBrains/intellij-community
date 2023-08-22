// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.folding

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings
import com.intellij.openapi.editor.FoldRegion
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase

class JavaFoldingTypeInferenceFoldingTest : JavaFoldingTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = LightJavaCodeInsightFixtureTestCase.JAVA_X

  private open class FoldRegionPart(val offset: Int)
  private class FoldRegionStart(offset: Int, val placeholder: String) : FoldRegionPart(offset)
  private class FoldRegionEnd(offset: Int) : FoldRegionPart(offset)

  fun renderTextWithFoldings(filter: (FoldRegion) -> Boolean = { true }): String {
    val editor = myFixture.editor
    val text = editor.document.text
    val foldRegions = editor.foldingModel.allFoldRegions
    val regions = ArrayList<FoldRegionPart>(foldRegions.size)
    // Need to separate to starts and ends because FoldRegion may contains inside another one
    for (region in foldRegions) {
      if (!filter(region)) continue
      regions.add(FoldRegionStart(region.startOffset, region.placeholderText))
      regions.add(FoldRegionEnd(region.endOffset))
    }
    regions.sortBy { it.offset }
    if (regions.isEmpty()) return text
    var start = 0
    var regionIndex = 0
    return buildString {
      do {
        val region = regions[regionIndex]
        append(text.substring(start, region.offset))
        when (region) {
          is FoldRegionStart -> append("""<fold placeholder="${region.placeholder}">""")
          is FoldRegionEnd -> append("""</fold>""")
        }
        start = region.offset
        regionIndex++
      } while(regionIndex < regions.size)
      append(text.substring(start))
    }
  }


  private val pattern = Regex("""<fold placeholder="(.*)">(.*)</fold>""")

  fun doTestAllHints(text: String) {
    val textWithoutMarks = text.replace(pattern) {
      it.groupValues[2]
    }
    configure(textWithoutMarks)
    TestCase.assertEquals(text, renderTextWithFoldings())
  }

  /**
   * Tests single hint range. Range should be in form of <fold placeholder="(.*)">(.*)</fold>
   */
  fun doTestHint(text: String) {
    var range : IntRange? = null
    var replacementCount = 0
    val textWithoutMarks = text.replace(pattern) {
      val start = it.range.start
      range = IntRange(start, start + it.groupValues[2].length)
      replacementCount++
      it.groupValues[2]
    }
    if (replacementCount > 1) fail("Expected one fold region")
    configure(textWithoutMarks)
    val finalRange = range
    if (replacementCount == 0 || finalRange == null) {
      TestCase.assertEquals(text, renderTextWithFoldings())
    } else {
      TestCase.assertEquals(text, renderTextWithFoldings {
        it.startOffset == finalRange.start && it.endOffset == finalRange.endInclusive
      })
    }
  }

  fun testLocalVariable() {
    JavaCodeFoldingSettings.getInstance().isReplaceVarWithInferredType = true
    doTestHint("""
      class A {
        void test() {
          <fold placeholder="String">var</fold> x = "FOO";
        }
      }""".trimIndent())
  }

  fun testVariableParameter() {
    JavaCodeFoldingSettings.getInstance().isReplaceVarWithInferredType = true
    doTestHint("""

      class A {
        void test(Iterable<String> i) {
          for (<fold placeholder="String">var</fold> x: i) {

          }
      }""".trimIndent())
  }

  fun testLambdaParameter() {
    JavaCodeFoldingSettings.getInstance().isReplaceVarWithInferredType = true
    doTestHint("""
      interface Consumer<T> {
        void accept(T t)
      }
      class A {
        void test() {
          Consumer<String> c = (<fold placeholder="String">var</fold> v) -> {};
      }""".trimIndent())
  }
}