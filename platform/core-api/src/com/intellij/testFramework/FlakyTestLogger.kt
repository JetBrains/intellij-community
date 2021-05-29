// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.TestOnly
import java.io.PrintWriter
import java.io.StringWriter

@Deprecated("this class is here only for debugging a flaky test, it will be deleted as soon problem will be resolved")
class FlakyTestLogger(val enabled: Boolean) : Disposable {

  private val lines = ArrayList<String>()

  override fun dispose() {
    lines.forEach(System.out::println)
  }

  fun append(line: String) {
    if (!enabled) return
    lines.add(line)
  }

  fun append(exception: Exception) {
    if (!enabled) return
    val stringWriter = StringWriter()
    exception.printStackTrace(PrintWriter(stringWriter))
    lines.add(stringWriter.toString())
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