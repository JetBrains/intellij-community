// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A typed char can finish a lookup inside the write action of the typed handler.
 * [CodeCompletionHandlerBase.handleCompletionElementSelected] must then run the later runnable of the insert handler after that write action,
 * because the runnable can start a new completion.
 */
class CompletionElementSelectedLaterRunnableTest : BasePlatformTestCase() {
  fun testLaterRunnableRunsOutsideOfTheCallerWriteAction() {
    myFixture.configureByText(PlainTextFileType.INSTANCE, "foo<caret>")
    var writeAccessInLaterRunnable: Boolean? = null
    val item = LookupElementBuilder.create("foo").withInsertHandler { context, _ ->
      context.laterRunnable = Runnable { writeAccessInLaterRunnable = ApplicationManager.getApplication().isWriteAccessAllowed }
    }
    val editor = myFixture.editor
    val offsetMap = OffsetMap(editor.document)
    offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, 0)
    offsetMap.addOffset(CompletionInitializationContext.SELECTION_END_OFFSET, 3)
    offsetMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, 3)

    WriteCommandAction.runWriteCommandAction(project) {
      CodeCompletionHandlerBase.createHandler(CompletionType.BASIC).handleCompletionElementSelected(
        item, listOf(item), '#', offsetMap, OffsetsInFile(myFixture.file, offsetMap), editor, editor.caretModel.offset
      )
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertEquals(false, writeAccessInLaterRunnable)
  }
}
