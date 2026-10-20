// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger

import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.ThrowableComputable
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.VirtualMachine
import org.jetbrains.org.objectweb.asm.Opcodes


/**
 * A debugger extension for bytecode inspections for different VMs.
 */
interface BytecodeInspector {
  /*
   Note:
   We already have a similar EP in `org.jetbrains.kotlin.idea.debugger.core.DexBytecodeInspector`
   but it is declared in the Kotlin Plugin debugger module and therefore is not visible from here.
   The `DexBytecodeInspector` is specific to Dex. In contrast, `BytecodeInspector` will be used for
   all VMs based on applicability.
 */

  /**
   * Returns true if the inspector is applicable to a VM.
   *
   * The first inspector to return true be used.
   */
  fun isApplicable(virtualMachine: VirtualMachine): Boolean

  /**
   * Returns a list of locations of RETURN statements found on specified line of a method.
   *
   * Returns null if vm lacks required capabilities.
   */
  fun findInlineConditionalReturnLocations(method: Method, lineNumber: Int): List<Location>?

  /**
   * Returns true if the location corresponds to a conditional return statement.
   */
  fun isConditionalReturnLocation(location: Location): Boolean

  companion object {
    internal val EP: ExtensionPointName<BytecodeInspector> =
      ExtensionPointName.create("com.intellij.debugger.bytecodeInspector")

    @JvmStatic
    fun getInstance(virtualMachine: VirtualMachine): BytecodeInspector? = EP.extensionList.firstOrNull { it.isApplicable(virtualMachine) }
  }
}
