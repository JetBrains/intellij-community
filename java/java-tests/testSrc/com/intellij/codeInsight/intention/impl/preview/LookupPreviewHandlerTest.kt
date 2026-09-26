// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.lookup.LookupArranger
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.junit.Test
import java.util.function.Function

class LookupPreviewHandlerTest : LightPlatformCodeInsightFixture4TestCase() {

  @Test
  fun testPreviewUpdatedWhenItemBecomesNull() {
    myFixture.configureByText("Test.java", "class Test {}")

    val settings = EditorSettingsExternalizable.getInstance()
    val originalPreviewSetting = settings.isShowIntentionPreview
    try {
      settings.isShowIntentionPreview = true

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

      assertTrue("Mapper should have been called during showInitially", mapperCalls.isNotEmpty())
      assertInstanceOf(mapperCalls.last(), LookupElement::class.java)

      mapperCalls.clear()
      lookup.setCurrentItem(null)

      handler.currentItemChanged(LookupEvent(lookup, false))

      assertTrue("Mapper should have been called on item change", mapperCalls.isNotEmpty())
      assertNull("Mapper should receive null when no suggestions are available", mapperCalls.last())

      handler.close()
    }
    finally {
      settings.isShowIntentionPreview = originalPreviewSetting
    }
  }
}
