// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij

import com.intellij.ide.rpc.deserializeFromRpc
import com.intellij.ide.rpc.serializeToRpc
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.icons.Icon
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class DefaultIconSerializationTest {
  @Test
  fun `should serialize and deserialize default icon`() {
    val iconManager = IntelliJIconManager()
    val icon2 = iconManager.icon {
      image("test.svg")
    }
    val icon1 = iconManager.icon {
      row {
        column {
          image("test.png", IntelliJIconManager::class.java.classLoader)
          image("test2.svg", IntelliJIconManager::class.java.classLoader)
        }
        icon(icon2)
      }
    }
    val serialized = serializeToRpc(icon1)
    val deserialized = deserializeFromRpc(serialized, Icon::class)
    assertTrue(deserialized == icon1)
  }
}