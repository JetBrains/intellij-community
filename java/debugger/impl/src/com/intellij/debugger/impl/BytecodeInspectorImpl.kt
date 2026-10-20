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
  override fun isApplicable(virtualMachine: VirtualMachine): Boolean = !DebuggerUtils.isAndroidVM(virtualMachine)

  override fun findInlineConditionalReturnLocations(method: Method, lineNumber: Int): List<Location>? {
    val vm = method.virtualMachine()
    if (!vm.canGetBytecodes() || !vm.canGetConstantPool()) {
      return null
    }
    return JavaLineBreakpointType.collectInlineConditionalReturnLocations(method, lineNumber).toList()
  }

  override fun isConditionalReturnLocation(location: Location): Boolean {
    if (!location.virtualMachine().canGetBytecodes()) {
      return false
    }
    val bytecodes = DebuggerUtilsEx.getMethod(location).bytecodes()
    val bytecodeOffs = location.codeIndex().toInt()

    // Implicit return instruction at the end of bytecode should not be treated as conditional return.
    // (Note that we also relay on the fact that all return instructions have no operands.)
    if (0 <= bytecodeOffs && bytecodeOffs < bytecodes.size - 1) {
      val opcode = bytecodes[bytecodeOffs].toInt() and 0xFF
      if (Opcodes.IRETURN <= opcode && opcode <= Opcodes.RETURN) {
        return true
      }
    }
    return false
  }
}
