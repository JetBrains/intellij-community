// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.TestOnly
import java.io.PrintWriter
import java.io.StringWriter

@Deprecated("this class is here only for debugging a flaky test, it will be deleted as soon problem will be resolved")
class FlakyTestLogger(val enabled: Boolean) : Disposable {


  data class Entry(val time: Long, val text: String)

  private val lists = ConcurrentCollectionFactory.createConcurrentIdentitySet<List<Entry>>()

  private val lines = ThreadLocal.withInitial { ArrayList<Entry>().also { lists.add(it) } }

  private fun add(str: String) {
    lines.get().add(Entry(System.currentTimeMillis(), """${System.currentTimeMillis()} ${Thread.currentThread().name} $str"""))
  }

  override fun dispose() {
    lists.flatten().sortedBy { it.time }.forEach { println(it.text) }
  }

  fun append(line: String) {
    if (!enabled) return
    add(line)
  }

  fun append(exception: Exception) {
    if (!enabled) return
    val stringWriter = StringWriter()
    exception.printStackTrace(PrintWriter(stringWriter))
    add(stringWriter.toString())
  }

  companion object {

    private val KEY = Key.create<FlakyTestLogger>("LogAccumulator")

    @JvmStatic
    fun get(): FlakyTestLogger? = TestModeFlags.get(KEY)

    @JvmStatic
    fun force(): FlakyTestLogger = get() ?: FlakyTestLogger(false)

    @JvmStatic
    fun isEnabled(): Boolean = TestModeFlags.get(KEY) != null

    @JvmStatic
    @TestOnly
    fun enable(disposer: Disposable) {
      TestModeFlags.set(KEY, FlakyTestLogger(true).also { Disposer.register(disposer, it) }, disposer)
    }

  }

}