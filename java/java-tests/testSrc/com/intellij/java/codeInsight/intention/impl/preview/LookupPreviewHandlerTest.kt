// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention.impl.preview

import com.intellij.codeInsight.intention.impl.preview.LookupPreviewHandler
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.lookup.LookupArranger
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Function

@TestApplication
class LookupPreviewHandlerTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testPreviewUpdatedWhenItemBecomesNull(): Unit = timeoutRunBlocking {
    myFixture.configureByText("Test.java", "class Test {}")
    val project = myFixture.project

    val settings = EditorSettingsExternalizable.getInstance()
    val originalPreviewSetting = settings.isShowIntentionPreview
    try {
      settings.isShowIntentionPreview = true

      withContext(Dispatchers.EDT) {
        val realItem = LookupElementBuilder.create("format")
        val lookup = LookupManager.getInstance(project).createLookup(
          myFixture.editor, arrayOf(realItem), "", LookupArranger.DefaultArranger()
        ) as LookupImpl

        val mapperCalls = mutableListOf<Any?>()

        val handler = LookupPreviewHandler<LookupElement>(project, lookup,
          Function { obj: Any? -> mapperCalls.add(obj); obj as? LookupElement },
          Function { _: LookupElement -> IntentionPreviewInfo.EMPTY },
        )

        handler.showInitially()
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertTrue(mapperCalls.isNotEmpty(), "Mapper should have been called during showInitially")
        assertInstanceOf(LookupElement::class.java, mapperCalls.last())

        mapperCalls.clear()
        lookup.setCurrentItem(null)

        handler.currentItemChanged(LookupEvent(lookup, false))

        assertTrue(mapperCalls.isNotEmpty(), "Mapper should have been called on item change")
        assertNull(mapperCalls.last(), "Mapper should receive null when no suggestions are available")

        handler.close()
      }
    }
    finally {
      settings.isShowIntentionPreview = originalPreviewSetting
    }
  }
}
