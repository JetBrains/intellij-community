// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.sun.jdi.VirtualMachine

object DexDebugFacility {
    fun isDex(virtualMachine: VirtualMachine): Boolean {
        return virtualMachine.name() == "Dalvik"
    }

    @Deprecated("Use isDex(virtualMachine)")
    fun isDex(debugProcess: DebugProcess): Boolean {
        val virtualMachineProxy = debugProcess.virtualMachineProxy as? VirtualMachineProxyImpl ?: return false
        return isDex(virtualMachineProxy.virtualMachine)
    }
}