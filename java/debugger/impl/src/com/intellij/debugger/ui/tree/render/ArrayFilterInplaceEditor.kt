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

import com.intellij.debugger.DebuggerBundle
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.actions.ArrayAction
import com.intellij.debugger.actions.ArrayFilterAction
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.Pair
import com.intellij.psi.JavaCodeFragment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.tree.TreeModelAdapter
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeInplaceEditor
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.sun.jdi.ArrayReference
import com.sun.jdi.ArrayType
import java.awt.Rectangle
import javax.swing.event.TreeModelEvent
import javax.swing.tree.TreeNode

/**
 * @author egor
 */
class ArrayFilterInplaceEditor(node: XDebuggerTreeNode, val myTemp: Boolean, thisType: PsiType?) : XDebuggerTreeInplaceEditor(node,
                                                                                                                              "arrayFilter") {
  init {
    if (thisType != null) {
      myExpressionEditor.setDocumentProcessor({ d ->
                                                val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(d)
                                                if (psiFile is JavaCodeFragment) psiFile.thisType = thisType
                                                d
                                              })
    }
    val arrayRenderer = ArrayAction.getArrayRenderer((myNode.parent as XValueNodeImpl).valueContainer)
    myExpressionEditor.expression = if (arrayRenderer is ArrayRenderer.Filtered) arrayRenderer.expression else null
  }

  override fun cancelEditing() {
    super.cancelEditing()
    if (myTemp) (myNode.parent as XValueNodeImpl).removeTemporaryEditorNode(myNode)
  }

  override fun doOKAction() {
    myTree.model.addTreeModelListener(object : TreeModelAdapter() {
      override fun process(event: TreeModelEvent?, type: EventType?) {
        if (event?.treePath?.lastPathComponent != myNode.parent) {
          myTree.model.removeTreeModelListener(this)
        }
        if (type == EventType.NodesInserted) {
          event?.children?.filter { ArrayFilterAction.isArrayFilter(it as TreeNode) }?.forEach {
            myTree.selectionPath = TreeUtil.getPathFromRoot(it as TreeNode)
            myTree.model.removeTreeModelListener(this)
          }
        }
      }
    })
    ArrayAction.setArrayRenderer(if (XDebuggerUtilImpl.isEmptyExpression(expression))
                                   NodeRendererSettings.getInstance().arrayRenderer
                                 else
                                   ArrayRenderer.Filtered(expression),
                                 myNode.parent as XValueNodeImpl,
                                 DebuggerManagerEx.getInstanceEx(project).context)
    super.doOKAction()
  }

  override fun getEditorBounds(): Rectangle? {
    val bounds = super.getEditorBounds() ?: return null

    val nameLabel = SimpleColoredComponent()
    nameLabel.ipad.right = 0
    nameLabel.ipad.left = 0
    nameLabel.icon = myNode.icon
    nameLabel.append(DebuggerBundle.message("message.node.filtered"), SimpleTextAttributes.REGULAR_ATTRIBUTES)
    val offset = nameLabel.preferredSize.width

    bounds.x += offset
    bounds.width -= offset
    return bounds
  }

  companion object {
    @JvmStatic
    fun edit(node: XDebuggerTreeNode, temp: Boolean) {
      val javaValue = (node.parent as XValueNodeImpl).valueContainer
      if (javaValue is JavaValue) {
        val debugProcess = javaValue.evaluationContext.debugProcess
        debugProcess.managerThread.schedule(
          object : SuspendContextCommandImpl(javaValue.evaluationContext.suspendContext) {
            override fun getPriority(): PrioritizedTask.Priority {
              return PrioritizedTask.Priority.NORMAL
            }

            override fun contextAction(suspendContext: SuspendContextImpl) {
              var type: String? = null
              val value = javaValue.descriptor.value
              if (value is ArrayReference) {
                type = (value.type() as ArrayType).componentTypeName()
              }
              else {
                val lastChildrenValue = ExpressionChildrenRenderer.getLastChildrenValue(javaValue.descriptor)
                if (lastChildrenValue is ArrayReference) {
                  // take first non-null element for now
                  for (v in lastChildrenValue.getValues(0, Math.min(lastChildrenValue.length(), 100))) {
                    if (v != null) {
                      type = v.type().name()
                      break
                    }
                  }
                }
              }
              val pair = ReadAction.compute<Pair<PsiElement, PsiType>, Exception> {
                DebuggerUtilsImpl.getPsiClassAndType(type, javaValue.project)
              }
              DebuggerUIUtil.invokeLater({ ArrayFilterInplaceEditor(node, temp, pair.second).show() })
            }

            override fun commandCancelled() {
              DebuggerUIUtil.invokeLater({ ArrayFilterInplaceEditor(node, temp, null).show() })
            }
          })
      }
      else {
        ArrayFilterInplaceEditor(node, temp, null).show()
      }
    }

    @JvmStatic
    fun editParent(parentNode: XValueNodeImpl) {
      var temp = false
      var node = parentNode.children.find { ArrayFilterAction.isArrayFilter(it) }
      if (node == null) {
        node = parentNode.addTemporaryEditorNode(AllIcons.General.Filter, DebuggerBundle.message("message.node.filtered"))
        temp = true
      }
      edit(node as XDebuggerTreeNode, temp)
    }
  }
}
