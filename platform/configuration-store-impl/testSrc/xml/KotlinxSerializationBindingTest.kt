// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(SettingsInternalApi::class)

package com.intellij.configurationStore.xml

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.JdomAdapter
import com.intellij.util.xmlb.KotlinxSerializationBinding
import com.intellij.util.xmlb.SettingsInternalApi
import com.intellij.util.xmlb.__json
import com.intellij.util.xmlb.clearKotlinxSerializationCache
import kotlinx.serialization.Serializable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KotlinxSerializationBindingTest {
  @Test
  fun `the cache clear replaces the json instance and keeps its configuration`() {
    val binding = KotlinxSerializationBinding(BindingTestState::class.java)
    val state = BindingTestState(foo = "hello")
    val element = binding.serialize(state, filter = null)
    val oldJson = __json

    clearKotlinxSerializationCache()

    assertThat(__json).isNotSameAs(oldJson)
    assertThat(JDOMUtil.write(binding.serialize(state, filter = null))).isEqualTo(JDOMUtil.write(element))
    assertThat(binding.deserialize(context = null, element = element, adapter = JdomAdapter)).isEqualTo(state)
    assertThat(binding.fromJson(currentValue = null, element = __json.parseToJsonElement("""{"foo": "a", "unknown": 1}""")))
      .isEqualTo(BindingTestState(foo = "a"))
  }
}

@Serializable
private data class BindingTestState(val foo: String = "", val bar: String = "")
