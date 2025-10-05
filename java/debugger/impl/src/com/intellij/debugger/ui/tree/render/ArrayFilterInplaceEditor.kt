// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.actions.ArrayAction
import com.intellij.debugger.actions.ArrayFilterAction
import com.intellij.debugger.actions.findJavaValue
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
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeInplaceEditor
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.sun.jdi.ArrayReference
import com.sun.jdi.ArrayType
import java.awt.Rectangle
import javax.swing.event.TreeModelEvent
import javax.swing.tree.TreeNode
import kotlin.math.min

class ArrayFilterInplaceEditor(
  node: XDebuggerTreeNode, private val myTemp: Boolean, thisType: PsiType?, sessionProxy: XDebugSessionProxy,
) : XDebuggerTreeInplaceEditor(node, "arrayFilter") {
  init {
    if (thisType != null) {
      myExpressionEditor.setDocumentProcessor { d ->
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(d)
        if (psiFile is JavaCodeFragment) psiFile.thisType = thisType
        d
      }
    }
    val arrayRenderer = ArrayAction.getArrayRenderer((myNode.parent as XValueNodeImpl).valueContainer, sessionProxy)
    myExpressionEditor.expression = if (arrayRenderer is ArrayRenderer.Filtered) arrayRenderer.expression else null
  }

  override fun cancelEditing() {
    super.cancelEditing()
    if (myTemp) (myNode.parent as XValueNodeImpl).removeTemporaryEditorNode(myNode)
  }

  override fun doOKAction() {
    myTree.model.addTreeModelListener(object : TreeModelAdapter() {
      override fun process(event: TreeModelEvent, type: EventType) {
        if (event.treePath?.lastPathComponent != myNode.parent) {
          myTree.model.removeTreeModelListener(this)
        }
        if (type == EventType.NodesInserted) {
          event.children?.filter { ArrayFilterAction.isArrayFilter(it as TreeNode) }?.forEach {
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
    nameLabel.append(JavaDebuggerBundle.message("message.node.filtered"), SimpleTextAttributes.REGULAR_ATTRIBUTES)
    val offset = nameLabel.preferredSize.width

    bounds.x += offset
    bounds.width -= offset
    return bounds
  }

  companion object {
    @JvmStatic
    fun edit(node: XDebuggerTreeNode, temp: Boolean, sessionProxy: XDebugSessionProxy) {
      val javaValue = findJavaValue((node.parent as XValueNodeImpl).valueContainer, sessionProxy)
      if (javaValue != null) {
        javaValue.evaluationContext.managerThread.schedule(
          object : SuspendContextCommandImpl(javaValue.evaluationContext.suspendContext) {
            override val priority: PrioritizedTask.Priority get() = PrioritizedTask.Priority.NORMAL

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
                  for (v in lastChildrenValue.getValues(0, min(lastChildrenValue.length(), 100))) {
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
              DebuggerUIUtil.invokeLater { ArrayFilterInplaceEditor(node, temp, pair.second, sessionProxy).show() }
            }

            override fun commandCancelled() {
              DebuggerUIUtil.invokeLater { ArrayFilterInplaceEditor(node, temp, null, sessionProxy).show() }
            }
          })
      }
      else {
        ArrayFilterInplaceEditor(node, temp, null, sessionProxy).show()
      }
    }

    @JvmStatic
    fun editParent(parentNode: XValueNodeImpl, sessionProxy: XDebugSessionProxy) {
      var temp = false
      var node = parentNode.children.find { ArrayFilterAction.isArrayFilter(it) }
      if (node == null) {
        node = parentNode.addTemporaryEditorNode(AllIcons.General.Filter, JavaDebuggerBundle.message("message.node.filtered"))
        temp = true
      }
      edit(node as XDebuggerTreeNode, temp, sessionProxy)
    }
  }
}
