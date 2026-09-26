// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup

import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.assertInstanceOf
import java.lang.reflect.Proxy
import javax.swing.JComponent
import javax.swing.JPanel

class LookupBottomPanelAdvertiserCustomizerTest : LightPlatformCodeInsightTestCase() {
  fun testThrowingCustomizerDoesNotPreventNextCustomizerFromProvidingComponent() {
    val advertiserComponent = JPanel()
    val markerComponent = JPanel()
    var result: JComponent? = null

    // The regression is in ordered EP processing: one broken plugin must not block later customizers
    // or force lookup initialization to fail instead of falling back to stock behavior.
    ExtensionTestUtil.maskExtensions(
      LookupBottomPanelAdvertiserCustomizer.EP_NAME,
      listOf(
        object : LookupBottomPanelAdvertiserCustomizer {
          override fun createAdvertiserComponent(lookup: Lookup, advertiserComponent: JComponent): JComponent? {
            error("test failure")
          }
        },
        object : LookupBottomPanelAdvertiserCustomizer {
          override fun createAdvertiserComponent(lookup: Lookup, advertiserComponent: JComponent): JComponent {
            return markerComponent
          }
        },
      ),
      testRootDisposable,
    )

    val loggedError = LoggedErrorProcessor.executeAndReturnLoggedError(Runnable {
      result = LookupBottomPanelAdvertiserCustomizer.getAdvertiserComponent(createLookup(), advertiserComponent)
    })

    assertInstanceOf<IllegalStateException>(loggedError)
    assertSame(markerComponent, result)
  }

  private fun createLookup(): Lookup {
    // The helper under test only passes Lookup through to extensions, so a minimal proxy keeps the
    // test focused on extension processing instead of real lookup UI setup.
    return Proxy.newProxyInstance(Lookup::class.java.classLoader, arrayOf(Lookup::class.java)) { proxy, method, args ->
      when (method.name) {
        "equals" -> proxy === args?.singleOrNull()
        "hashCode" -> 0
        "toString" -> "LookupProxy"
        else -> defaultValue(method.returnType)
      }
    } as Lookup
  }

  private fun defaultValue(returnType: Class<*>): Any? {
    return when (returnType) {
      Boolean::class.javaPrimitiveType -> false
      Byte::class.javaPrimitiveType -> 0.toByte()
      Char::class.javaPrimitiveType -> 0.toChar()
      Double::class.javaPrimitiveType -> 0.0
      Float::class.javaPrimitiveType -> 0f
      Int::class.javaPrimitiveType -> 0
      Long::class.javaPrimitiveType -> 0L
      Short::class.javaPrimitiveType -> 0.toShort()
      else -> null
    }
  }
}
