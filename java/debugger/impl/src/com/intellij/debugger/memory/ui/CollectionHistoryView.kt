// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.ui

import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerUtilsImpl.getValueMarkers
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.debugger.memory.utils.InstanceJavaValue
import com.intellij.debugger.memory.utils.InstanceValueDescriptor
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl
import com.intellij.debugger.ui.impl.watch.MessageDescriptor
import com.intellij.debugger.ui.impl.watch.NodeDescriptorProvider
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl
import com.intellij.debugger.ui.tree.FieldDescriptor
import com.intellij.debugger.ui.tree.NodeDescriptor
import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.debugger.ui.tree.render.ChildrenBuilder
import com.intellij.debugger.ui.tree.render.ClassRenderer
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener
import com.intellij.debugger.ui.tree.render.NodeRenderer
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.intellij.xdebugger.memory.ui.InstancesTree
import com.sun.jdi.BooleanValue
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.TreeSelectionListener

@ApiStatus.Experimental
class CollectionHistoryView(private val myClsName: String,
                            private val myFieldName: String,
                            debugProcess: XDebugProcess,
                            private val myValueNode: XValueNodeImpl?) {
  private val DEFAULT_SPLITTER_PROPORTION = 0.5f
  private val MAX_INSTANCES_NUMBER: Long = 1000000

  private val myDebugProcess = (debugProcess as JavaDebugProcess).debuggerSession.process
  private val myDebugSession = debugProcess.session
  private val mySuspendContext = myDebugProcess.suspendManager.pausedContext
  private val myStackFrameList = StackFrameList(myDebugProcess)
  private val mySplitter = JBSplitter(false, DEFAULT_SPLITTER_PROPORTION)
  private val myNodeManager = MyNodeManager(myDebugSession.project)
  private val myHistoryInstancesTree: InstancesTree = InstancesTree(myDebugProcess.project, myDebugSession.debugProcess.editorsProvider,
                                                                    getValueMarkers(myDebugProcess)) { }
  private val myHistoryTree: InstancesTree = InstancesTree(myDebugProcess.project, myDebugSession.debugProcess.editorsProvider,
                                                           getValueMarkers(myDebugProcess)) { }
  private val myMainComponent: JComponent

  init {
    setupHistoryTreeSelectionListener()

    mySplitter.setHonorComponentsMinimumSize(false)
    mySplitter.firstComponent = JBScrollPane(myHistoryTree)
    mySplitter.secondComponent = JBScrollPane(myStackFrameList)

    if (myValueNode == null) {
      setupInstancesTree()
      val splitter = JBSplitter(false, 0.3F)
      splitter.setHonorComponentsMinimumSize(false)
      splitter.firstComponent = JBScrollPane(myHistoryInstancesTree)
      splitter.secondComponent = mySplitter
      myMainComponent = splitter
    }
    else {
      loadHistory()
      myMainComponent = mySplitter
    }
  }

  private fun setupInstancesTree() {
    myHistoryInstancesTree.addTreeSelectionListener(TreeSelectionListener {
      myHistoryTree.addChildren(createChildren(listOf(), null), true)
      myHistoryTree.rebuildTree(InstancesTree.RebuildPolicy.RELOAD_INSTANCES)
      invokeInDebuggerThread {
        val selectionPath = it.path
        val node = selectionPath?.lastPathComponent as? XValueNodeImpl ?: return@invokeInDebuggerThread
        val clsType = (node.valueContainer as? JavaValue)?.descriptor?.type?.name() ?: return@invokeInDebuggerThread
        loadFieldHistory(clsType, myFieldName, node)
      }
    })

    myHistoryInstancesTree.addChildren(createChildren(listOf(), null), true)
    invokeInDebuggerThread {
      val virtualMachineProxy = getVirtualMachine() ?: return@invokeInDebuggerThread
      val classes = virtualMachineProxy.allClasses().filter { it.name().replace("$", ".") == myClsName }
      val instances = classes.flatMap { it.instances(MAX_INSTANCES_NUMBER) }
      invokeLater { myHistoryInstancesTree.addChildren(createChildren(instances, null), true) }
    }
  }

  private fun setupHistoryTreeSelectionListener() {
    myHistoryTree.addChildren(createChildren(listOf(), CollectionHistoryRenderer()), true)
    myHistoryTree.addTreeSelectionListener(TreeSelectionListener {
      myStackFrameList.setFrameItems(listOf())
      val selectedNode = getSelectedNode(myHistoryTree) ?: return@TreeSelectionListener
      val parentNode = getParentNode(myHistoryTree)

      val parentRow = myHistoryTree.root.children.withIndex().firstOrNull { pair -> pair.value == selectedNode }?.index
      val childRow = parentNode?.children?.withIndex()?.firstOrNull { pair -> pair.value == selectedNode }?.index

      invokeInDebuggerThread {
        val virtualMachineProxy = getVirtualMachine() ?: return@invokeInDebuggerThread
        val items = if (parentNode == myHistoryTree.root) {
          val descriptor = (myValueNode?.valueContainer as? JavaValue)?.descriptor as? FieldDescriptor
          var clsName = descriptor?.field?.declaringType()?.name()
          if (clsName == null) {
            clsName = (getSelectedNode(myHistoryInstancesTree)?.valueContainer as? JavaValue)?.descriptor?.type?.name()
          }

          val parent = myValueNode?.parent ?: getSelectedNode(myHistoryInstancesTree)
          val field = getObjectReferenceForNode(parent as? XValueNodeImpl)?.referenceType()?.fieldByName(myFieldName)
                      ?: return@invokeInDebuggerThread
          val clsInstance = if (!field.isStatic) getObjectReferenceForNode(parent as? XValueNodeImpl) else null
          val modificationIndex = virtualMachineProxy.mirrorOf(parentRow!!)
          CollectionBreakpointUtils.getFieldModificationStack(mySuspendContext, myFieldName, clsName, clsInstance, modificationIndex)
        }
        else {
          if (childRow == null) return@invokeInDebuggerThread
          val modificationIndex = virtualMachineProxy.mirrorOf(childRow)
          val collectionInstance = getObjectReferenceForNode(parentNode) ?: return@invokeInDebuggerThread

          CollectionBreakpointUtils.getCollectionModificationStack(mySuspendContext, collectionInstance, modificationIndex)
        }

        invokeLater { myStackFrameList.setFrameItems(items) }
      }
    })
  }

  private fun loadFieldHistory(clsName: String, fieldName: String, parent: XValueNodeImpl) {
    val field = getObjectReferenceForNode(parent)?.referenceType()?.fieldByName(fieldName) ?: return
    val clsInstance = if (!field.isStatic) getObjectReferenceForNode(parent as? XValueNodeImpl) else null

    val fieldModifications = CollectionBreakpointUtils.getFieldModificationsHistory(mySuspendContext, fieldName, clsName, clsInstance)

    invokeLater { myHistoryTree.addChildren(createChildren(fieldModifications, CollectionHistoryRenderer()), true) }
  }


  private fun loadHistory() {
    myValueNode?.let { node ->
      invokeInDebuggerThread {
        val descriptor = (node.valueContainer as? JavaValue)?.descriptor as? FieldDescriptor ?: return@invokeInDebuggerThread
        val field = descriptor.field
        val parent = node.parent as? XValueNodeImpl ?: return@invokeInDebuggerThread
        loadFieldHistory(field.declaringType().name(), field.name(), parent)
      }
    }
  }

  private fun createChildren(values: List<Value>, renderer: NodeRenderer?): XValueChildrenList {
    val children = XValueChildrenList()
    mySuspendContext.managerThread.invokeAndWait(object : DebuggerCommandImpl() {
      override fun action() {
        for (ins in values) {
          val evalContext = EvaluationContextImpl(mySuspendContext, mySuspendContext.frameProxy)
          val value = InstanceJavaValue(JavaReferenceInfo(ins as ObjectReference).createDescriptor(myDebugProcess.project), evalContext,
                                        myNodeManager)
          if (renderer != null) {
            value.descriptor.setRenderer(renderer)
          }
          children.add(value)
        }
      }
    })
    return children
  }

  private fun invokeInDebuggerThread(runnable: () -> Unit) {
    mySuspendContext.managerThread.schedule(object : DebuggerCommandImpl() {
      override fun action() {
        runnable()
      }
    })
  }

  private fun invokeLater(runnable: () -> Unit) {
    ApplicationManager.getApplication().invokeLater(runnable)
  }

  private fun getVirtualMachine(): VirtualMachineProxyImpl? {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return mySuspendContext.frameProxy?.virtualMachine
  }

  private fun getParentNode(tree: InstancesTree): XValueNodeImpl? {
    val selectionPath = tree.selectionPath
    val selectedItem = selectionPath?.lastPathComponent as? XValueNodeImpl ?: return null
    return selectedItem.parent as? XValueNodeImpl
  }

  private fun getSelectedNode(tree: InstancesTree): XValueNodeImpl? {
    val selectionPath = tree.selectionPath
    return selectionPath?.lastPathComponent as? XValueNodeImpl ?: return null
  }

  private fun getObjectReferenceForNode(node: XValueNodeImpl?): ObjectReference? {
    val descriptor = node?.valueContainer as? NodeDescriptorProvider ?: return null
    return (descriptor.descriptor as? ValueDescriptor)?.value as? ObjectReference
  }

  fun getComponent(): JComponent {
    return myMainComponent
  }

  private class MyNodeManager(project: Project?) : NodeManagerImpl(project, null) {
    override fun createNode(descriptor: NodeDescriptor, evaluationContext: EvaluationContext): DebuggerTreeNodeImpl {
      return DebuggerTreeNodeImpl(null, descriptor)
    }

    override fun createMessageNode(descriptor: MessageDescriptor): DebuggerTreeNodeImpl {
      return DebuggerTreeNodeImpl(null, descriptor)
    }

    override fun createMessageNode(message: String): DebuggerTreeNodeImpl {
      return DebuggerTreeNodeImpl(null, MessageDescriptor(message))
    }
  }

  private inner class CollectionHistoryRenderer : ClassRenderer() {

    override fun buildChildren(value: Value?, builder: ChildrenBuilder?, evaluationContext: EvaluationContext?) {
      if (evaluationContext == null || builder == null) return
      val collectionInstance = value as? ObjectReference ?: return

      val collectionModifications = CollectionBreakpointUtils.getCollectionModificationsHistory(mySuspendContext, collectionInstance)

      val nodes = collectionModifications
        .filterIsInstance<ObjectReference>()
        .map {
          val modificationInfo = CollectionBreakpointUtils.getCollectionModificationInfo(myDebugProcess, evaluationContext, it)
          val element = modificationInfo?.first
          val isAddition = modificationInfo?.second
          Pair(element, isAddition)
        }
        .filter { it.first is ObjectReference && it.second is BooleanValue }
        .map {
          val descriptor = InstanceValueDescriptor(myDebugSession.project, it.first)
          val element = it.first
          val isAddition = it.second?.value()
          descriptor.setRenderer(ChildrenRenderer(element, isAddition))
          myNodeManager.createNode(descriptor, evaluationContext)
        }

      builder.setChildren(nodes)
    }

    private inner class ChildrenRenderer(val obj: ObjectReference?, val isAddition: Boolean?) : ClassRenderer() {
      override fun calcValueIcon(descriptor: ValueDescriptor?,
                                 evaluationContext: EvaluationContext?,
                                 listener: DescriptorLabelListener?): Icon {
        return if (isAddition != null && isAddition) AllIcons.General.Add else AllIcons.General.Remove
      }

      private fun evaluate(evaluationContext: EvaluationContext): Pair<Value, Value>? {
        val cls = myDebugProcess.findClass(evaluationContext, "com.intellij.rt.debugger.agent.CollectionBreakpointInstrumentor\$Pair",
                                           null) as? ClassType ?: return null
        val getKey = DebuggerUtils.findMethod(cls, "getKey", "()Ljava/lang/Object;") ?: return null
        val getValue = DebuggerUtils.findMethod(cls, "getValue", "()Ljava/lang/Object;") ?: return null
        val key = myDebugProcess.invokeInstanceMethod(evaluationContext, obj!!, getKey, listOf(), 0)
        val value = myDebugProcess.invokeInstanceMethod(evaluationContext, obj, getValue, listOf(), 0)
        return Pair(key, value)
      }

      private fun isMap(): Boolean {
        val javaValue = myValueNode?.valueContainer as? JavaValue ?: return false
        val type = javaValue.descriptor.type ?: return false
        val project = myDebugProcess.project
        val psiType = PsiType.getTypeByName(type.name(), project, GlobalSearchScope.allScope(project))
        val resolved = PsiUtil.resolveClassInClassTypeOnly(psiType) ?: return false
        return InheritanceUtil.isInheritor(resolved, CommonClassNames.JAVA_UTIL_MAP)
      }

      override fun calcLabel(descriptor: ValueDescriptor?,
                             evaluationContext: EvaluationContext?,
                             labelListener: DescriptorLabelListener?): String {
        if (isMap()) {
          val pair = evaluate(evaluationContext!!)
          val key = pair?.first
          val value = pair?.second
          val keyDescriptor = InstanceValueDescriptor(myDebugSession.project, key)
          val valueDescriptor = InstanceValueDescriptor(myDebugSession.project, value)
          return "$keyDescriptor -> $valueDescriptor"
        }
        return super.calcLabel(descriptor, evaluationContext, labelListener)
      }
    }
  }
}