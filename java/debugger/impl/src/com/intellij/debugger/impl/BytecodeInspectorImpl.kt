// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.debugger.BytecodeInspector
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.VirtualMachine
import org.jetbrains.org.objectweb.asm.Opcodes

/**
 * A [BytecodeInspector] for Java VM
 */
class BytecodeInspectorImpl : BytecodeInspector {
  override fun isApplicable(virtualMachine: VirtualMachine): Boolean {
    return !DebuggerUtils.isAndroidVM(virtualMachine) && virtualMachine.canGetBytecodes() && virtualMachine.canGetConstantPool()
  }

  override fun findInlineConditionalReturnLocations(method: Method, lineNumber: Int): List<Location> {
    return JavaLineBreakpointType.collectInlineConditionalReturnLocations(method, lineNumber).toList()
  }

  override fun isConditionalReturnLocation(location: Location): Boolean {
    val method = DebuggerUtilsEx.getMethod(location) ?: return false
    val bytecodes = method.bytecodes()
    val bytecodeOffset = Math.toIntExact(location.codeIndex())
    // The final implicit return must not be treated as a conditional return.
    if (bytecodeOffset in 0 until bytecodes.size - 1) {
      val opcode = bytecodes[bytecodeOffset].toInt() and 0xFF
      if (opcode in Opcodes.IRETURN..Opcodes.RETURN) {
        return true
      }
    }
    return false
  }
}
