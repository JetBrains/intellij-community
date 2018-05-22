// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.classFilter.ClassFilter
import com.intellij.util.ArrayUtil
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties
import java.util.*

/**
 * @author egor
 */
internal abstract class BreakpointIntentionAction(protected val myBreakpoint: XBreakpoint<*>, text: String) : AnAction(text) {

  internal class AddCallerNotFilter(breakpoint: XBreakpoint<*>, private val myCaller: String) :
    BreakpointIntentionAction(breakpoint,
                              "Do not stop if called from: ${StringUtil.getShortName(StringUtil.substringBefore(myCaller, "(")!!)}") {

    override fun actionPerformed(e: AnActionEvent) {
      with(myBreakpoint.properties as JavaBreakpointProperties<*>) {
        isCALLER_FILTERS_ENABLED = true
        val callerFilter = ClassFilter(myCaller)
        callerFilters = ArrayUtil.remove(callerFilters, callerFilter)
        callerExclusionFilters = ArrayUtil.append(callerExclusionFilters, callerFilter)
      }
      (myBreakpoint as XBreakpointBase<*, *, *>).fireBreakpointChanged()
    }
  }

  internal class AddCallerFilter(breakpoint: XBreakpoint<*>, private val myCaller: String) :
    BreakpointIntentionAction(breakpoint,
                              "Stop only if called from: ${StringUtil.getShortName(StringUtil.substringBefore(myCaller, "(")!!)}") {

    override fun actionPerformed(e: AnActionEvent) {
      with(myBreakpoint.properties as JavaBreakpointProperties<*>) {
        isCALLER_FILTERS_ENABLED = true
        val callerFilter = ClassFilter(myCaller)
        callerFilters = ArrayUtil.append(callerFilters, callerFilter)
        callerExclusionFilters = ArrayUtil.remove(callerExclusionFilters, callerFilter)
      }
      (myBreakpoint as XBreakpointBase<*, *, *>).fireBreakpointChanged()
    }
  }

  internal class AddInstanceFilter(breakpoint: XBreakpoint<*>, private val myInstance: Long) :
    BreakpointIntentionAction(breakpoint, "Stop only in the current object") {

    override fun actionPerformed(e: AnActionEvent) {
      with(myBreakpoint.properties as JavaBreakpointProperties<*>) {
        isINSTANCE_FILTERS_ENABLED = true
        addInstanceFilter(myInstance)
      }
      (myBreakpoint as XBreakpointBase<*, *, *>).fireBreakpointChanged()
    }
  }

  internal class AddClassFilter(breakpoint: XBreakpoint<*>, private val myClass: String) :
    BreakpointIntentionAction(breakpoint, "Stop only in the class: ${StringUtil.getShortName(myClass)}") {

    override fun actionPerformed(e: AnActionEvent) {
      with(myBreakpoint.properties as JavaBreakpointProperties<*>) {
        isCLASS_FILTERS_ENABLED = true
        val classFilter = ClassFilter(myClass)
        classFilters = ArrayUtil.append(classFilters, classFilter)
        classExclusionFilters = ArrayUtil.remove(classExclusionFilters, classFilter)
      }
      (myBreakpoint as XBreakpointBase<*, *, *>).fireBreakpointChanged()
    }
  }

  internal class AddClassNotFilter(breakpoint: XBreakpoint<*>, private val myClass: String) :
    BreakpointIntentionAction(breakpoint, "Do not stop in the class: ${StringUtil.getShortName(myClass)}") {

    override fun actionPerformed(e: AnActionEvent) {
      with(myBreakpoint.properties as JavaBreakpointProperties<*>) {
        isCLASS_FILTERS_ENABLED = true
        val classFilter = ClassFilter(myClass)
        classExclusionFilters = ArrayUtil.append(classExclusionFilters, classFilter)
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

          frameDescriptor.thisObject?.uniqueID()?.let {
            res.add(AddInstanceFilter(breakpoint, it))
          }

          if (Registry.`is`("debugger.breakpoints.caller.filter")) {
            frameDescriptor.getUserData(CALLER_KEY)?.let {
              res.add(AddCallerFilter(breakpoint, it))
              res.add(AddCallerNotFilter(breakpoint, it))
            }
          }
        }

        return res
      }
      return emptyList()
    }
  }
}
