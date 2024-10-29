// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.util.TinyTree
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus

/**
 * The presentation is saved into a [TinyTree] during its construction to be compact.
 * Each node has tag (one of [InlayTags]) and reference data (specific for node).
 * Rules:
 * List has tag [InlayTags.LIST_TAG] and no data
 *
 * Collapsible list has one of the tags [InlayTags.COLLAPSIBLE_LIST_IMPLICITLY_COLLAPSED_TAG],
 * [InlayTags.COLLAPSIBLE_LIST_IMPLICITLY_EXPANDED_TAG], [InlayTags.COLLAPSIBLE_LIST_EXPLICITLY_EXPANDED_TAG],
 * [InlayTags.COLLAPSIBLE_LIST_EXPLICITLY_COLLAPSED_TAG] and no data.
 *
 * Collapsible list always has 2 branches (each is a separate node) with tags [InlayTags.COLLAPSIBLE_LIST_EXPANDED_BRANCH_TAG] and
 * [InlayTags.COLLAPSIBLE_LIST_COLLAPSED_BRANCH_TAG] with no data.
 *
 * Text node has tag [InlayTags.TEXT_TAG] and either [String] or [ActionWithContent] (with String content) data,
 * depending on whether the click handler is available.
 */
@ApiStatus.Internal
class PresentationTreeBuilderImpl private constructor(
  private val index: Byte,
  private val context: InlayTreeBuildingContext
) : CollapsiblePresentationTreeBuilder {
  companion object {
    fun createRoot(position: InlayPosition? = null): PresentationTreeBuilderImpl {
      if (position == null && !ApplicationManager.getApplication().isUnitTestMode) {
        throw IllegalArgumentException()
      }

      val context = InlayTreeBuildingContext(position ?: InlineInlayPosition(0, false))
      return PresentationTreeBuilderImpl(0, context)
    }

    const val MAX_NODE_COUNT: Int = 100
    const val MAX_SEGMENT_TEXT_LENGTH: Int = 30

    const val DOESNT_FIT_INDEX: Byte = -10
  }


  override fun toggleButton(builder: PresentationTreeBuilder.() -> Unit) {
    val buttonIndex = context.addNode(parent = index, nodePayload = InlayTags.COLLAPSE_BUTTON_TAG, data = null)
    val childrenBuilder = PresentationTreeBuilderImpl(buttonIndex, context)
    builder(childrenBuilder)
  }

  override fun list(builder: PresentationTreeBuilder.() -> Unit) {
    val listIndex = context.addNode(parent = index, nodePayload = InlayTags.LIST_TAG, data = null)
    val childrenBuilder = PresentationTreeBuilderImpl(listIndex, context)
    builder(childrenBuilder)
  }

  override fun collapsibleList(state: CollapseState,
                               expandedState: CollapsiblePresentationTreeBuilder.() -> Unit,
                               collapsedState: CollapsiblePresentationTreeBuilder.() -> Unit) {
    val tag = when (state) {
      CollapseState.Expanded -> InlayTags.COLLAPSIBLE_LIST_IMPLICITLY_EXPANDED_TAG
      CollapseState.Collapsed -> InlayTags.COLLAPSIBLE_LIST_IMPLICITLY_COLLAPSED_TAG
      CollapseState.NoPreference -> if (context.depth < 1) InlayTags.COLLAPSIBLE_LIST_IMPLICITLY_EXPANDED_TAG else InlayTags.COLLAPSIBLE_LIST_IMPLICITLY_COLLAPSED_TAG
    }
    val listIndex = context.addNode(
      parent = index,
      nodePayload = tag,
      data = null
    )

    fun addChild(nodePayload: Byte, childBuilder: CollapsiblePresentationTreeBuilder.() -> Unit) {
      val childIndex = context.addNode(listIndex, nodePayload, null)
      context.depth++
      try {
        childBuilder(PresentationTreeBuilderImpl(childIndex, context))
      } finally {
        context.depth--
      }
    }

    fun addExpandedChild() = addChild(InlayTags.COLLAPSIBLE_LIST_EXPANDED_BRANCH_TAG, expandedState)
    fun addCollapsedChild() = addChild(InlayTags.COLLAPSIBLE_LIST_COLLAPSED_BRANCH_TAG, collapsedState)

    if (tag == InlayTags.COLLAPSIBLE_LIST_IMPLICITLY_EXPANDED_TAG) {
      addExpandedChild()
      addCollapsedChild()
    }
    else {
      addCollapsedChild()
      addExpandedChild()
    }
  }

  override fun text(text: String, actionData: InlayActionData?) {
    require(text.isNotEmpty()) { "Text entry may not be empty. Please, fix the provider implementation." }
    val segmentText = if (context.isTruncateTextNodes() && MAX_SEGMENT_TEXT_LENGTH < text.length) {
      text.substring(0, MAX_SEGMENT_TEXT_LENGTH) + "…"
    } else {
      text
    }
    context.textElementCount++
    context.addNode(parent = index, nodePayload = InlayTags.TEXT_TAG, if (actionData != null) ActionWithContent(actionData, segmentText) else segmentText)
  }

  override fun clickHandlerScope(actionData: InlayActionData, builder: PresentationTreeBuilder.() -> Unit) {
    val nodeIndex = context.addNode(parent = index, nodePayload = InlayTags.CLICK_HANDLER_SCOPE_TAG, data = actionData)
    val treeBuilder = PresentationTreeBuilderImpl(nodeIndex, context)
    builder(treeBuilder)
  }

  //override fun icon(icon: Icon, actionData: InlayActionData?) {
  //  context.addNode(parent = index, nodePayload = InlayTags.ICON_TAG, icon)
  //}

  fun complete() : TinyTree<Any?> {
    val tree = context.tree
    tree.reverseChildren()
    require(context.textElementCount != 0)
    return tree
  }
}

@ApiStatus.Internal
class ActionWithContent(
  val actionData: InlayActionData,
  val content: Any
)

private class InlayTreeBuildingContext(private val position: InlayPosition) {
  private var nodeCount = 1
  private var limitReached = false
  var textElementCount = 0
  var depth: Int = 0

  // the tree in data may contain String, Icon or Byte (in case of collapse button, which means )
  val tree: TinyTree<Any?> = TinyTree(InlayTags.LIST_TAG, null)

  fun addNode(parent: Byte, nodePayload: Byte, data: Any?): Byte {
    if (limitReached) {
      return PresentationTreeBuilderImpl.DOESNT_FIT_INDEX
    }
    if (nodeCount == PresentationTreeBuilderImpl.MAX_NODE_COUNT - 1) {
      limitReached = true
      tree.add(parent, InlayTags.TEXT_TAG, "…")
      return PresentationTreeBuilderImpl.DOESNT_FIT_INDEX
    }
    nodeCount++
    return tree.add(parent, nodePayload, data)
  }

  fun isTruncateTextNodes() = position is InlineInlayPosition
}