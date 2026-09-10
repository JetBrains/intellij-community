// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.library

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async

@Service(Service.Level.PROJECT)
class JavaLibraryHolder(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) : Disposable.Default {

  @Volatile
  private var libraries: Deferred<Libraries> = produceRecalculation()

  init {
    project.messageBus.connect(this)
      .subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
        override fun rootsChanged(event: ModuleRootEvent) {
          val current = libraries
          libraries = produceRecalculation()
          current.cancel(NewRootsArrived())
        }
      })
  }

  private class NewRootsArrived : CancellationException()

  @RequiresReadLock
  @JvmName("getLibraries")
  internal fun getLibraries(): Libraries {
    ThreadingAssertions.assertReadAccess()
    val captured = libraries
    captured.start()
    if (captured.isCompleted) {
      try {
        @OptIn(ExperimentalCoroutinesApi::class)
        return captured.getCompleted()
      } catch (_: CancellationException) {
        // no-op, will proceed with the following lines
      }
    }
    val newlyComputedLibraries = doComputeLibraries()
    libraries = CompletableDeferred(newlyComputedLibraries)
    captured.cancel()
    return newlyComputedLibraries
  }

  /**
   * Returns `null` if the libraries are not ready. This function can be used in read-lock-free environments such as UI thread
   */
  @JvmName("getLibrariesOrNull")
  internal fun getLibrariesOrNull(): Libraries? {
    val captured = libraries
    captured.start()
    if (!captured.isCompleted) {
      return null
    }
    return try {
      @OptIn(ExperimentalCoroutinesApi::class)
      captured.getCompleted()
    }
    catch (_: CancellationException) {
      return null
    }
  }

  private fun produceRecalculation(): Deferred<Libraries> {
    return coroutineScope.async(Dispatchers.Default, start = CoroutineStart.LAZY) {
      readAction {
        doComputeLibraries()
      }
    }
  }

  @RequiresReadLock
  private fun doComputeLibraries(): Libraries {
    return JavaLibraryUtil.fillLibraries(OrderEnumerator.orderEntries(project), true)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): JavaLibraryHolder = project.service()
  }
}
