// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.impl

import com.intellij.model.psi.PsiSymbolService
import com.intellij.navigation.SymbolNavigationService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat

class SymbolNavigationServiceImplTest : BasePlatformTestCase() {
  fun `test invalid psi symbol does not produce navigation target`() {
    val file = myFixture.configureByText(PlainTextFileType.INSTANCE, "before")
    val element = file.findElementAt(0)!!
    val symbol = PsiSymbolService.getInstance().asSymbol(element)

    ApplicationManager.getApplication().runWriteAction {
      myFixture.editor.document.setText("after")
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    assertThat(element.isValid).isFalse()
    assertThat(SymbolNavigationService.getInstance().getNavigationTargets(project, symbol)).isEmpty()
  }
}
