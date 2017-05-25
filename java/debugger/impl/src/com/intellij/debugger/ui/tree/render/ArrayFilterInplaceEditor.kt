/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.ui.tree.render

import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeInplaceEditor
import com.intellij.xdebugger.impl.ui.tree.nodes.MessageTreeNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

/**
 * @author egor
 */
class ArrayFilterInplaceEditor(node: XDebuggerTreeNode, val res: AsyncPromise<ArrayRenderer>) : XDebuggerTreeInplaceEditor(node, "arrayFilter") {
  override fun cancelEditing() {
    super.cancelEditing()
    (myNode.parent as XValueNodeImpl).removeTemporaryEditorNode(myNode)
    res.setError("Cancelled")
  }

  override fun doOKAction() {
    res.setResult(if (XDebuggerUtilImpl.isEmptyExpression(expression))
                    NodeRendererSettings.getInstance().arrayRenderer
                  else
                    ArrayRenderer.Filtered(expression))
    super.doOKAction()
  }

  companion object {
    @JvmStatic
    fun edit(parentNode: XValueNodeImpl,
             original: ArrayRenderer): Promise<ArrayRenderer> {
      val res = AsyncPromise<ArrayRenderer>()
      val node = if (original is ArrayRenderer.Filtered) {
        parentNode.children.find { it is MessageTreeNode && it.link === ArrayRenderer.Filtered.FILTER_HYPERLINK } as XDebuggerTreeNode
      }
      else {
        parentNode.addTemporaryEditorNode()
      }
      DebuggerUIUtil.invokeLater({ArrayFilterInplaceEditor(node, res).show()})
      return res
    }
  }
}
