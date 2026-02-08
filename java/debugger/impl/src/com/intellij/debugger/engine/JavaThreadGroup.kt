// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.icons.AllIcons
import com.intellij.xdebugger.frame.XExecutionStackGroup
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import javax.swing.Icon

private class JavaThreadGroup(
  name: String,
  override val groups: List<JavaThreadGroup>,
  override val stacks: List<JavaExecutionStack>
) : XExecutionStackGroup(name) {
  override val icon: Icon
    get() = AllIcons.Debugger.ThreadGroup

  companion object {
    // Here we load all the groups at once. For Java groups it's not a problem, there are few,
    // but if virtual thread groups are introduced to XThreadsView, execution stacks should be loaded lazily.
    @ApiStatus.Internal
    @JvmStatic
    fun buildJavaThreadGroup(group: ThreadGroupReferenceProxyImpl, debugProcess: DebugProcessImpl, currentThread: ThreadReferenceProxyImpl?): CompletableFuture<JavaThreadGroup> {
      val name = group.name()
      val subGroups = group.threadGroups().map { buildJavaThreadGroup(it, debugProcess, currentThread) }
      val stacks = group.threads().map { thread -> JavaExecutionStack.create(thread, debugProcess, thread == currentThread) }
      return CompletableFuture.allOf(*(subGroups + stacks).toTypedArray())
        .thenApply {
          val subGroups = subGroups.mapNotNull { it.join() }
          val stacks = stacks.mapNotNull { it.join() }
          JavaThreadGroup(name, subGroups, stacks)
        }
    }
  }
}