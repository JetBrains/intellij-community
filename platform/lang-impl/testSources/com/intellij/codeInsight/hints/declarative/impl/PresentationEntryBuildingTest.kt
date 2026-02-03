// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.views.InlayPresentationEntry
import com.intellij.codeInsight.hints.declarative.impl.views.PresentationEntryBuilder
import com.intellij.codeInsight.hints.declarative.impl.views.TextInlayPresentationEntry
import com.intellij.testFramework.UsefulTestCase

class PresentationEntryBuildingTest : UsefulTestCase() {
  fun testSimpleText() {
    testBuildEntries(txt("hello"), txt("world!")) {
      text("hello")
      text("world!")
    }
  }

  fun testFoldedText() {
    testBuildEntries(txt("1"), txt("2"), txt("3")) {
      text("1")
      list {
        text("2")
      }
      text("3")
    }
  }

  fun testCollapsibleListInExpandedState() {
    testBuildEntries(txt("collapsed")) {
      collapsibleList(
        CollapseState.Collapsed,
        expandedState = {
          text("expanded")
        },
        collapsedState = {
          text("collapsed")
        })
    }
  }

  fun testCollapsibleListInCollapsedState() {
    testBuildEntries(txt("expanded")) {
      collapsibleList(
        CollapseState.Expanded,
        expandedState = {
          text("expanded")
        },
        collapsedState = {
          text("collapsed")
        })
    }
  }

  fun testCollapsibleWithButtonExpanded() {
    testBuildEntries(txt("List"), txt("<"), txt("String"), txt(">"), b = { stringListGenerics(CollapseState.Expanded) })
  }

  fun testCollapsibleWithButtonCollapsed() {
    testBuildEntries(txt("List"), txt("<...>"), b = { stringListGenerics(CollapseState.Collapsed) })
  }

  fun testTextNoClick() {
    testClickSegments(ClickSegment("text", actionData = null)) {
      text("text")
    }
  }

  fun testTextWithHandler() {
    val actionData = InlayActionData(StringInlayActionPayload("payload"), "handler.id")
    testClickSegments(ClickSegment("text", actionData = actionData)) {
      text("text", actionData = actionData)
    }
  }

  fun testTextWithHandler2() {
    val actionData = InlayActionData(StringInlayActionPayload("payload"), "handler.id")
    testClickSegments(ClickSegment("ab", actionData = actionData)) {
      text("a", actionData = actionData)
      text("b", actionData = actionData)
    }
  }

  fun testClickHandlerScope() {
    val actionData = InlayActionData(StringInlayActionPayload("payload"), "handler.id")
    testClickSegments(ClickSegment("ab", actionData = actionData)) {
      clickHandlerScope(actionData) {
        text("a")
        text("b")
      }
    }
  }


  fun testListCollapsesWhenTooDeep() {
    testBuildText("List<List<...>>") {
      genericList(CollapseState.NoPreference) {
        genericList(CollapseState.NoPreference) {
          genericList(CollapseState.NoPreference) {
            genericList(CollapseState.NoPreference) {
              text("Hello")
            }
          }
        }
      }
    }
  }

  fun testTooLongList() {
    val expected = ArrayList<TextInlayPresentationEntry>(PresentationTreeBuilderImpl.MAX_NODE_COUNT - 1) // + root
    repeat(PresentationTreeBuilderImpl.MAX_NODE_COUNT - 2) {
      expected.add(txt("a"))
    }
    expected.add(txt("…"))
    testBuildEntries(*expected.toTypedArray()) {
      repeat(200) {
        text("a")
      }
    }
  }

  private fun PresentationTreeBuilder.stringListGenerics(state: CollapseState) {
    genericList(state) {
      text("String")
    }
  }

  private fun PresentationTreeBuilder.genericList(state: CollapseState, content: CollapsiblePresentationTreeBuilder.() -> Unit) {
    text("List")
    collapsibleList(
      state,
      expandedState = {
        toggleButton {
          text("<")
        }
        content()
        toggleButton {
          text(">")
        }
      },
      collapsedState = {
        toggleButton {
          text("<...>")
        }
      })
  }

  private fun txt(str: String): TextInlayPresentationEntry {
    return TextInlayPresentationEntry(str, clickArea = null)
  }

  private fun testBuildText(expected: String, b: PresentationTreeBuilder.() -> Unit) {
    val treeBuilder = PresentationTreeBuilderImpl.createRoot()
    b(treeBuilder)
    val entryBuilder = PresentationEntryBuilder(treeBuilder.complete(), PresentationEntryBuildingTest::class.java)
    val entries = entryBuilder.buildPresentationEntries().toList()
    assertEquals(expected, entries.joinToString(separator = "") { (it as TextInlayPresentationEntry).text })
  }

  private fun testBuildEntries(vararg expected: InlayPresentationEntry, b: PresentationTreeBuilder.() -> Unit) {
    val treeBuilder = PresentationTreeBuilderImpl.createRoot()
    b(treeBuilder)
    val entryBuilder = PresentationEntryBuilder(treeBuilder.complete(), PresentationEntryBuildingTest::class.java)
    val entries = entryBuilder.buildPresentationEntries().toList()
    assertEquals(expected.toList(), entries)
  }


  private fun testClickSegments(vararg expected: ClickSegment, b: PresentationTreeBuilder.() -> Unit) {
    val treeBuilder = PresentationTreeBuilderImpl.createRoot()
    b(treeBuilder)
    val entryBuilder = PresentationEntryBuilder(treeBuilder.complete(), PresentationEntryBuildingTest::class.java)
    val entries = entryBuilder.buildPresentationEntries().toList()
    val clickSegments = ArrayList<ClickSegment>()
    var previousActionData: InlayActionData? = null
    val segmentText = StringBuilder()
    for (entry in entries) {
      entry as TextInlayPresentationEntry
      val clickArea = entry.clickArea
      val actionData = clickArea?.actionData
      if (actionData != previousActionData) {
        if (segmentText.isNotEmpty()) {
          clickSegments.add(ClickSegment(segmentText.toString(), previousActionData))
          segmentText.clear()
        }
        segmentText.append(entry.text)
        previousActionData = actionData
      }
      else {
        segmentText.append(entry.text)
      }
    }
    if (segmentText.isNotEmpty()) {
      clickSegments.add(ClickSegment(segmentText.toString(), previousActionData))
    }
    assertEquals(expected.toList(), clickSegments)
  }

  private data class ClickSegment(val text: String, val actionData: InlayActionData?)
}