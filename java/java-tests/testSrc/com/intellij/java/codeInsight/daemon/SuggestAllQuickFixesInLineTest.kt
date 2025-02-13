// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.codeInsight.daemon.impl.IntentionsUI
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.testFramework.enableInspectionTool
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class SuggestAllQuickFixesInLineTest : LightJavaCodeInsightFixtureTestCase() {
  fun testPreferFixesFromOffset() {
    enableInspectionTool(project, UnusedDeclarationInspection(true), testRootDisposable)
    myFixture.configureByText("Foo.java",
                              "class Test {\n" +
                              "    int fo<caret>o; int bar = UnknownMethod();\n" +
                              "}")
    myFixture.doHighlighting()
    val allActions = IntentionsUI.getInstance(project).getCachedIntentions(editor, file).allActions
    assertNotNull(allActions.first {  it.text == "Safe delete 'foo'" })
    assertNotNull(allActions.first {  it.text == "Create method 'UnknownMethod' in 'Test'" })

    editor.caretModel.moveToOffset(editor.caretModel.offset + 20)
    myFixture.doHighlighting()
    val newActions = IntentionsUI.getInstance(project).getCachedIntentions(editor, file).allActions
    assertNotNull(newActions.first {  it.text == "Create method 'UnknownMethod' in 'Test'" })
  }
}