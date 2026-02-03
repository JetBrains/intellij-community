// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.CollapseState
import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.codeInsight.hints.declarative.impl.views.PresentationEntryBuilder
import com.intellij.codeInsight.hints.declarative.impl.views.TextInlayPresentationEntry
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import org.junit.Assert
import com.intellij.codeInsight.hints.declarative.TinyTreeDebugNode as DebugNode

class PresentationTreeBuilderTest : UsefulTestCase() {
  fun testText() {
    val treeBuilder = PresentationTreeBuilderImpl.createRoot()
    with(treeBuilder) {
      text("hello")
      text("world!")
    }
    val tree = treeBuilder.complete()
    val debugTree = DebugNode.buildDebugTree(tree)
    val helloNode = DebugNode<Any?>(InlayTags.TEXT_TAG, "hello", mutableListOf())
    val worldNode = DebugNode<Any?>(InlayTags.TEXT_TAG, "world!", mutableListOf())
    val root = DebugNode(InlayTags.LIST_TAG, null, mutableListOf(helloNode, worldNode))
    TestCase.assertEquals(root, debugTree)
  }

  fun testCollapsibleList() {
    val treeBuilder = PresentationTreeBuilderImpl.createRoot()
    with(treeBuilder) {
      collapsibleList(
        CollapseState.Expanded,
        expandedState = {
          text("first")
        },
        collapsedState = {
          text("second")
        })
    }
    val tree = treeBuilder.complete()
    val debugTree = DebugNode.buildDebugTree(tree)
    val firstNode = DebugNode<Any?>(InlayTags.TEXT_TAG, "first", mutableListOf())
    val secondNode = DebugNode<Any?>(InlayTags.TEXT_TAG, "second", mutableListOf())
    val expandedBranchNode = DebugNode(InlayTags.COLLAPSIBLE_LIST_EXPANDED_BRANCH_TAG, null, mutableListOf(firstNode))
    val collapsedBranchNode = DebugNode(InlayTags.COLLAPSIBLE_LIST_COLLAPSED_BRANCH_TAG, null, mutableListOf(secondNode))
    val collapsibleList = DebugNode(InlayTags.COLLAPSIBLE_LIST_IMPLICITLY_EXPANDED_TAG, null, mutableListOf(expandedBranchNode, collapsedBranchNode))
    val root = DebugNode(InlayTags.LIST_TAG, null, mutableListOf(collapsibleList))
    TestCase.assertEquals(root, debugTree)
  }

  fun testList() {
    val treeBuilder = PresentationTreeBuilderImpl.createRoot()
    with(treeBuilder) {
      list {
        text("first")
        list {
          text("folded")
        }
        text("second")
      }
    }
    val tree = treeBuilder.complete()
    val debugTree = DebugNode.buildDebugTree(tree)
    val firstNode = DebugNode<Any?>(InlayTags.TEXT_TAG, "first", mutableListOf())
    val secondNode = DebugNode<Any?>(InlayTags.TEXT_TAG, "second", mutableListOf())
    val foldedTextNode = DebugNode<Any?>(InlayTags.TEXT_TAG, "folded", mutableListOf())
    val foldedListNode = DebugNode(InlayTags.LIST_TAG, null, mutableListOf(foldedTextNode))
    val outerListNode = DebugNode(InlayTags.LIST_TAG, null, mutableListOf(firstNode, foldedListNode, secondNode))
    val root = DebugNode(InlayTags.LIST_TAG, null, mutableListOf(outerListNode))
    TestCase.assertEquals(root, debugTree)
  }

  fun testClickScopeHandler() {
    val treeBuilder = PresentationTreeBuilderImpl.createRoot()
    val actionData = InlayActionData(StringInlayActionPayload("payload"), "handler.id")
    with(treeBuilder) {
      clickHandlerScope(actionData) {
        text("text")
      }
    }
    val tree = treeBuilder.complete()
    val debugTree = DebugNode.buildDebugTree(tree)
    val textNode = DebugNode<Any?>(InlayTags.TEXT_TAG, "text", mutableListOf())
    val clickHandlerNode = DebugNode(InlayTags.CLICK_HANDLER_SCOPE_TAG, actionData, mutableListOf(textNode))
    val root = DebugNode(InlayTags.LIST_TAG, null, mutableListOf(clickHandlerNode))
    TestCase.assertEquals(root, debugTree)
  }

  fun testTooLongText() {
    val treeBuilder = PresentationTreeBuilderImpl.createRoot()
    with(treeBuilder) {
      text("a".repeat(40))
    }
    val tree = treeBuilder.complete()
    val debugTree = DebugNode.buildDebugTree(tree)
    val firstNode = DebugNode<Any?>(InlayTags.TEXT_TAG, "a".repeat(30) + "…", mutableListOf())
    val root = DebugNode(InlayTags.LIST_TAG, null, mutableListOf(firstNode))
    TestCase.assertEquals(root, debugTree)
  }

  // TODO add tests when there is too long text

  fun testCollapsedWithTooManyChildren() {
    val treeBuilder = PresentationTreeBuilderImpl.createRoot()
    with(treeBuilder) {
      collapsibleList(
        CollapseState.Collapsed,
        expandedState = {
          repeat(PresentationTreeBuilderImpl.MAX_NODE_COUNT) {
            text("text $it")
          }
        },
        collapsedState = {
          text("...")
        }
      )
    }
    val tree = treeBuilder.complete()
    val entries = PresentationEntryBuilder(tree, PresentationTreeBuilderTest::class.java).buildPresentationEntries()
    Assert.assertArrayEquals(arrayOf(TextInlayPresentationEntry("...", -1, null)), entries)
  }
}