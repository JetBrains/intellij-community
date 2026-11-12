// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl

import com.intellij.openapi.components.service
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test

/**
 * [TemplateSettingsReloader.reload] is the production path of a reload after a dynamic plugin load.
 */
@TestApplication
internal class TemplateSettingsReloaderTest {
  @Test
  fun `a background reload applies the parsed templates and keeps their set`() {
    val settings = TemplateSettings.getInstance()
    val before = settings.templates.associateBy(::templateKey)
    assertFalse(before.isEmpty(), "The test needs a default live template")

    timeoutRunBlocking {
      service<TemplateSettingsReloader>().reload()
    }

    val after = settings.templates.associateBy(::templateKey)
    assertEquals(before.keys.sorted(), after.keys.sorted())
    // the parse creates each template again, so the same instance means that the reload applied nothing
    for ((key, template) in after) {
      assertNotSame(before[key], template, key)
    }
  }

  private fun templateKey(template: TemplateImpl): String = "${template.groupName}/${template.key}"
}
