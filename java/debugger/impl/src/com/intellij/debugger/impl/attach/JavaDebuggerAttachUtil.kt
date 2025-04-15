// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.debugger.impl.attach

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.openapi.project.Project
import com.sun.tools.attach.AttachNotSupportedException
import com.sun.tools.attach.VirtualMachine
import sun.jvmstat.monitor.*
import java.io.IOException

object JavaDebuggerAttachUtil {
  @JvmStatic
  fun canAttach(processHandler: BaseProcessHandler<*>): Boolean = JavaAttachDebuggerProvider.getProcessAttachInfo(processHandler) != null

  @JvmStatic
  fun attach(processHandler: BaseProcessHandler<*>, project: Project?): Boolean {
    val info = JavaAttachDebuggerProvider.getProcessAttachInfo(processHandler)
    if (info != null) {
      JavaAttachDebuggerProvider.attach(info, project)
      return true
    }
    return false
  }

  @JvmStatic
  fun getAttachedPids(project: Project): Set<String> {
    return DebuggerManagerEx.getInstanceEx(project).getSessions()
      .asSequence()
      .map { it.debugEnvironment.getRemoteConnection() }
      .filterIsInstance<PidRemoteConnection>()
      .map { it.pid }
      .toHashSet()
  }

  @JvmStatic
  @Throws(IOException::class, AttachNotSupportedException::class)
  fun attachVirtualMachine(id: String): VirtualMachine {
    testAttachable(id)
    return VirtualMachine.attach(id)
  }

  @JvmStatic
  fun isAttachable(id: String): Boolean {
    try {
      testAttachable(id)
      return true
    }
    catch (_: AttachNotSupportedException) {
      return false
    }
  }

  /**
   * Modified version of sun.tools.attach.HotSpotAttachProvider.testAttachable
   * original version silently fails and allows attach to openj9 jvms (and crash them)
   */
  @Throws(AttachNotSupportedException::class)
  private fun testAttachable(id: String) {
    var vm: MonitoredVm? = null
    try {
      val host = MonitoredHost.getMonitoredHost(HostIdentifier(null as String?))
      vm = host.getMonitoredVm(VmIdentifier(id))
      if (!MonitoredVmUtil.isAttachable(vm)) {
        throw AttachNotSupportedException("Vm is not attachable")
      }
    }
    catch (e: Throwable) {
      throw AttachNotSupportedException("Unable to attach").apply { initCause(e) }
    }
    finally {
      vm?.detach()
    }
  }
}
