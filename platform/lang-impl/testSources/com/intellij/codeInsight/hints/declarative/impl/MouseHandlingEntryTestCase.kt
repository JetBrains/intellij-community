// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.CollapseState
import com.intellij.codeInsight.hints.declarative.CollapsiblePresentationTreeBuilder
import com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder
import com.intellij.codeInsight.hints.declarative.impl.util.TinyTree
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import junit.framework.TestCase
import org.junit.Test

class MouseHandlingEntryTestCase : LightPlatformCodeInsightFixture4TestCase() {
  @Test
  fun testCollapseExpand1() {
    testClick("collapse", "expand", "collapse") {
      collapsibleList(CollapseState.Expanded, expandedState = {
        toggleButton {
          text("collapse")
        }
      }, collapsedState = {
        toggleButton {
          text("expand")
        }
      })
    }
  }

  @Test
  fun testCollapseExpand2() {
    testClick("List|<|String|>", "List|<...>", "<") {
      genericList(CollapseState.Expanded) { text("String") }
    }
  }

  @Test
  fun testCollapseExpand3() {
    testClick("List|<...>", "List|<|String|>", "<...>") {
      genericList(CollapseState.Collapsed) { text("String") }
    }
  }

  @Test
  fun testUpdateAfterCollapse1() {
    testCollapseAndUpdate(
      initialStateBuilder = {
        genericList(CollapseState.Expanded) { text("String") }
      },
      updatedStateBuilder = {
        genericList(CollapseState.Expanded) { text("Integer") }
      },
      beforeClickText = "List|<|String|>",
      clickPlace = "<",
      afterClickText = "List|<...>",
      afterUpdateText = "List|<...>"
    )
  }

  @Test
  fun testUpdateAfterCollapse2() {
    testCollapseAndUpdate(
      initialStateBuilder = {
        genericList(CollapseState.Expanded) { text("String") }
      },
      updatedStateBuilder = {
        genericList(CollapseState.Expanded) { genericList(CollapseState.Expanded) { text("Integer") } }
      },
      beforeClickText = "List|<|String|>",
      clickPlace = "<",
      afterClickText = "List|<...>",
      afterUpdateText = "List|<...>"
    )
  }

  @Test
  fun testUpdateAfterCollapse3() {
    testCollapseAndUpdate(
      initialStateBuilder = {
        genericList(CollapseState.Expanded) { genericList(CollapseState.Expanded) { text("Integer") } }
      },
      updatedStateBuilder = {
        genericList(CollapseState.Expanded) { text("String") }
      },
      beforeClickText = "List|<|List|<|Integer|>|>",
      clickPlace = "<",
      afterClickText = "List|<...>",
      afterUpdateText = "List|<...>"
    )
  }

  @Test
  fun testUpdateAfterCollapse4() {
    testCollapseAndUpdate(
      initialStateBuilder = {
        genericMap(
          CollapseState.Expanded,
          keyBuilder = {
            genericList(CollapseState.Expanded) { text("Integer") }
          },
          valueBuilder = {
            text("String")
          }
        )
      },
      updatedStateBuilder = {
        genericMap(
          CollapseState.Expanded,
          keyBuilder = {
            text("String")
          },
          valueBuilder = {
            genericList(CollapseState.Expanded) { text("Integer") }

          }
        )
      },
      beforeClickText = "Map|<|List|<|Integer|>|, |String|>",
      clickPlace = "<",
      occurenceIndex = 1,
      afterClickText = "Map|<|List|<...>|, |String|>",
      afterUpdateText = "Map|<|String|, |List|<|Integer|>|>"
    )
  }

  @Test
  fun testUpdateAfterCollapse5() {
    testCollapseAndUpdate(
      initialStateBuilder = {
        genericMap(
          CollapseState.Expanded,
          keyBuilder = {
            genericList(CollapseState.Expanded) { text("Integer") }
          },
          valueBuilder = {
            text("String")
          }
        )
      },
      updatedStateBuilder = {
        genericMap(
          CollapseState.Expanded,
          keyBuilder = {
            genericMap(
              CollapseState.Expanded,
              keyBuilder = {
                text("A")
              },
              valueBuilder = {
                text("B")
              }
            )
          },
          valueBuilder = {
            genericList(CollapseState.Expanded) { text("Integer") }

          }
        )
      },
      beforeClickText = "Map|<|List|<|Integer|>|, |String|>",
      clickPlace = "<",
      occurenceIndex = 1,
      afterClickText = "Map|<|List|<...>|, |String|>",
      afterUpdateText = "Map|<|Map|<...>|, |List|<|Integer|>|>"
    )
  }

  private fun testCollapseAndUpdate(initialStateBuilder: PresentationTreeBuilder.() -> Unit,
                                    updatedStateBuilder: PresentationTreeBuilder.() -> Unit,
                                    beforeClickText: String,
                                    clickPlace: String,
                                    occurenceIndex: Int = 0,
                                    afterClickText: String,
                                    afterUpdateText: String
  ) {
    myFixture.configureByText("test.txt", "my text")
    val state = buildState {
      initialStateBuilder()
    }
    val presentationList = InlayPresentationList(state, true, false)
    val beforeClickEntries = presentationList.getEntries().toList()
    assertEquals(beforeClickText, toText(beforeClickEntries))
    var occurence = 0
    for (beforeClickEntry in beforeClickEntries) {
      if ((beforeClickEntry as TextInlayPresentationEntry).text == clickPlace) {
        if (occurence == occurenceIndex) {
          beforeClickEntry.handleClick(myFixture.editor, presentationList)
          break
        }
        occurence++
      }
    }
    val afterClickEntries = presentationList.getEntries().toList()
    assertEquals(afterClickText, toText(afterClickEntries))
    val newState = buildState {
      updatedStateBuilder()
    }
    presentationList.updateState(newState, false, false)
    val updatedStateEntries = presentationList.getEntries().toList()
    assertEquals(afterUpdateText, toText(updatedStateEntries))
  }

  private fun PresentationTreeBuilder.genericList(state: CollapseState,
                                                  insideGeneric: CollapsiblePresentationTreeBuilder.() -> Unit) {
    text("List")
    collapsibleList(state, expandedState = {
      toggleButton {
        text("<")
      }
      insideGeneric()
      toggleButton {
        text(">")
      }
    }, collapsedState = {
      toggleButton {
        text("<...>")
      }
    })
  }

  private fun PresentationTreeBuilder.genericMap(state: CollapseState,
                                                 keyBuilder: CollapsiblePresentationTreeBuilder.() -> Unit,
                                                 valueBuilder: CollapsiblePresentationTreeBuilder.() -> Unit,
                                                 ) {
    text("Map")
    collapsibleList(state, expandedState = {
      toggleButton {
        text("<")
      }
      keyBuilder()
      text(", ")
      valueBuilder()
      toggleButton {
        text(">")
      }
    }, collapsedState = {
      toggleButton {
        text("<...>")
      }
    })
  }


  private fun buildState(b: PresentationTreeBuilder.() -> Unit): TinyTree<Any?> {
    val root = PresentationTreeBuilderImpl.createRoot()
    with(root) {
      b()
    }
    return root.complete()
  }

  private fun testClick(beforeClick: String, afterClick: String, click: String, b: PresentationTreeBuilder.() -> Unit) {
    myFixture.configureByText("test.txt", "my text")
    val root = PresentationTreeBuilderImpl.createRoot()
    b(root)
    val presentationList = InlayPresentationList(root.complete(), true, false)
    val beforeClickEntries = presentationList.getEntries().toList()
    TestCase.assertEquals(beforeClick, toText(beforeClickEntries))
    beforeClickEntries.find { (it as TextInlayPresentationEntry).text == click }!!.handleClick(myFixture.editor, presentationList)
    val afterClickEntries = presentationList.getEntries().toList()
    TestCase.assertEquals(afterClick, toText(afterClickEntries))
  }

  private fun toText(entries: List<InlayPresentationEntry>): String {
    return entries.joinToString(separator = "|") { (it as TextInlayPresentationEntry).text }
  }
}