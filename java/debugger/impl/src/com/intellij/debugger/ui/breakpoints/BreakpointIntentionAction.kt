// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints

import com.intellij.debugger.InstanceFilter
import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsActions.ActionText
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.classFilter.ClassFilter
import com.intellij.util.ArrayUtil
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties

internal abstract class BreakpointIntentionAction(protected val myBreakpoint: XBreakpoint<*>, @ActionText text: String) : AnAction(text) {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  internal class AddCallerNotFilter(breakpoint: XBreakpoint<*>, private val myCaller: String) :
    BreakpointIntentionAction(breakpoint, JavaDebuggerBundle.message(
      "action.do.not.stop.if.called.from.text", StringUtil.getShortName(StringUtil.substringBefore(myCaller, "(") ?: myCaller))) {

    override fun update(e: AnActionEvent) {
      with(myBreakpoint.properties as JavaBreakpointProperties<*>) {
        e.presentation.setEnabled(!isCALLER_FILTERS_ENABLED || !callerExclusionFilters.contains(ClassFilter(myCaller)))
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      with(myBreakpoint.properties as JavaBreakpointProperties<*>) {
        isCALLER_FILTERS_ENABLED = true
        val callerFilter = ClassFilter(myCaller)
        callerFilters = ArrayUtil.remove(callerFilters, callerFilter)
        callerExclusionFilters = appendIfNeeded(callerExclusionFilters, callerFilter)
      }
      (myBreakpoint as XBreakpointBase<*, *, *>).fireBreakpointChanged()
    }
  }

  internal class AddCallerFilter(breakpoint: XBreakpoint<*>, private val myCaller: String) :
    BreakpointIntentionAction(breakpoint,
                              JavaDebuggerBundle.message("action.stop.only.if.called.from.text", StringUtil.getShortName(StringUtil.substringBefore(myCaller, "(") ?: myCaller))) {

    override fun update(e: AnActionEvent) {
      with(myBreakpoint.properties as JavaBreakpointProperties<*>) {
        e.presentation.setEnabled(!isCALLER_FILTERS_ENABLED || !callerFilters.contains(ClassFilter(myCaller)))
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      with(myBreakpoint.properties as JavaBreakpointProperties<*>) {
        isCALLER_FILTERS_ENABLED = true
        val callerFilter = ClassFilter(myCaller)
        callerFilters = appendIfNeeded(callerFilters, callerFilter)
        callerExclusionFilters = ArrayUtil.remove(callerExclusionFilters, callerFilter)
      }
      (myBreakpoint as XBreakpointBase<*, *, *>).fireBreakpointChanged()
    }
  }

  internal class AddInstanceFilter(breakpoint: XBreakpoint<*>, private val myInstance: Long) :
    BreakpointIntentionAction(breakpoint, JavaDebuggerBundle.message("action.stop.only.in.current.object.text")) {

    override fun update(e: AnActionEvent) {
      with(myBreakpoint.properties as JavaBreakpointProperties<*>) {
        e.presentation.setEnabled(!isINSTANCE_FILTERS_ENABLED || !instanceFilters.contains(InstanceFilter.create(myInstance)))
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      with(myBreakpoint.properties as JavaBreakpointProperties<*>) {
        isINSTANCE_FILTERS_ENABLED = true
        instanceFilters = appendIfNeeded(instanceFilters, InstanceFilter.create(myInstance))
      }
      (myBreakpoint as XBreakpointBase<*, *, *>).fireBreakpointChanged()
    }
  }

  internal class AddClassFilter(breakpoint: XBreakpoint<*>, private val myClass: String) :
    BreakpointIntentionAction(breakpoint, JavaDebuggerBundle.message("action.stop.only.in.class.text", StringUtil.getShortName(myClass))) {

    override fun update(e: AnActionEvent) {
      with(myBreakpoint.properties as JavaBreakpointProperties<*>) {
        e.presentation.setEnabled(!isCLASS_FILTERS_ENABLED || !classFilters.contains(ClassFilter(myClass)))
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      with(myBreakpoint.properties as JavaBreakpointProperties<*>) {
        isCLASS_FILTERS_ENABLED = true
        val classFilter = ClassFilter(myClass)
        classFilters = appendIfNeeded(classFilters, classFilter)
        classExclusionFilters = ArrayUtil.remove(classExclusionFilters, classFilter)
      }
      (myBreakpoint as XBreakpointBase<*, *, *>).fireBreakpointChanged()
    }
  }

  internal class AddClassNotFilter(breakpoint: XBreakpoint<*>, private val myClass: String) :
    BreakpointIntentionAction(breakpoint, JavaDebuggerBundle.message("action.do.not.stop.in.class.text", StringUtil.getShortName(myClass))) {

    override fun update(e: AnActionEvent) {
      with(myBreakpoint.properties as JavaBreakpointProperties<*>) {
        e.presentation.setEnabled(!isCLASS_FILTERS_ENABLED || !classExclusionFilters.contains(ClassFilter(myClass)))
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      with(myBreakpoint.properties as JavaBreakpointProperties<*>) {
        isCLASS_FILTERS_ENABLED = true
        val classFilter = ClassFilter(myClass)
        classExclusionFilters = appendIfNeeded(classExclusionFilters, classFilter)
        classFilters = ArrayUtil.remove(classFilters, classFilter)
      }
      (myBreakpoint as XBreakpointBase<*, *, *>).fireBreakpointChanged()
    }
  }

  companion object {
    @JvmField
    val CALLER_KEY = Key.create<String>("CALLER_KEY")

    @JvmField
    val THIS_TYPE_KEY = Key.create<String>("THIS_TYPE_KEY")

    @JvmField
    val THIS_ID_KEY = Key.create<Long>("THIS_ID_KEY")

    @JvmStatic
    fun getIntentions(breakpoint: XBreakpoint<*>, currentSession: XDebugSession?): List<AnAction> {
      val process = currentSession?.debugProcess
      if (process is JavaDebugProcess) {
        val res = ArrayList<AnAction>()

        val currentStackFrame = currentSession.currentStackFrame
        if (currentStackFrame is JavaStackFrame) {
          val frameDescriptor = currentStackFrame.descriptor

          frameDescriptor.getUserData(THIS_TYPE_KEY)?.let {
            res.add(AddClassFilter(breakpoint, it))
            res.add(AddClassNotFilter(breakpoint, it))
          }

          frameDescriptor.getUserData(THIS_ID_KEY)?.let {
            res.add(AddInstanceFilter(breakpoint, it))
          }

          frameDescriptor.getUserData(CALLER_KEY)?.let {
            res.add(AddCallerFilter(breakpoint, it))
            res.add(AddCallerNotFilter(breakpoint, it))
          }
        }

        return res
      }
      return emptyList()
    }

    private fun <T> appendIfNeeded(array: Array<T>, element: T): Array<T> {
      return if (array.contains(element)) {
        array
      }
      else {
        ArrayUtil.append(array, element)
      }
    }
  }
}
