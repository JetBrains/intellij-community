// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.backend

import com.intellij.debugger.memory.action.DebuggerTreeAction
import com.intellij.debugger.memory.filtering.ClassInstancesProvider
import com.intellij.debugger.memory.ui.InstancesWindow
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerLuxActionsApi
import com.intellij.openapi.application.EDT
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModel
import com.intellij.xdebugger.impl.rpc.XValueId
import com.sun.jdi.ReferenceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class BackendJavaDebuggerLuxActionsApi : JavaDebuggerLuxActionsApi {
  override suspend fun showInstancesDialog(xValueId: XValueId) {
    val xValueModel = BackendXValueModel.findById(xValueId) ?: return
    val xValue = xValueModel.xValue
    val session = xValueModel.session
    val objectRef = DebuggerTreeAction.getObjectReference(xValue) ?: return
    val referenceType: ReferenceType = objectRef.referenceType()
    withContext(Dispatchers.EDT) {
      InstancesWindow(session, ClassInstancesProvider(referenceType), referenceType).show()
    }
  }
}