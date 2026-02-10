// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixtures
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.icons.DynamicIcon
import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.Icon
import org.jetbrains.icons.IconManager
import org.jetbrains.icons.dynamicIcon
import org.jetbrains.icons.imageIcon
import org.jetbrains.icons.rendering.IconRendererManager
import org.jetbrains.icons.rendering.RenderingContext
import org.jetbrains.icons.rendering.createRenderer
import org.junit.jupiter.api.Test

@OptIn(DelicateCoroutinesApi::class)
@TestApplication
class DynamicIconTest {
  @OptIn(ExperimentalIconsApi::class)

  @Test
  fun `should be properly updated after serialization and deserialization`() {
    IntelliJIconManager.activate()
    runBlocking {
      val binary = imageIcon("fileTypes/binaryData.svg")
      val css = imageIcon("fileTypes/css.svg")
      val dynamicIcon = dynamicIcon(binary)
      val json = Json {
        serializersModule = IconManager.getInstance().getSerializersModule()
      }
      val serialized = json.encodeToString(dynamicIcon)
      val deserialized = json.decodeFromString<Icon>(serialized)
      var lastChangeId = 0
      val flow = IconRendererManager.createUpdateFlow(this) { changeId ->
        lastChangeId = changeId
      }
      assertThat(deserialized).isInstanceOf(DynamicIcon::class.java)
      val deserializedDynamicIcon = deserialized as DynamicIcon
      val icon = deserializedDynamicIcon.createRenderer(RenderingContext(flow))
      assertThat(deserializedDynamicIcon.getCurrentIcon()).isEqualTo(binary)
      val asyncTask = GlobalScope.launch {
        delay(1000)
        dynamicIcon.swap(css)
      }
      asyncTask.join()
      assertThat(deserializedDynamicIcon.getCurrentIcon()).isEqualTo(css)
      assertThat(lastChangeId).isGreaterThan(0)
    }
  }
}