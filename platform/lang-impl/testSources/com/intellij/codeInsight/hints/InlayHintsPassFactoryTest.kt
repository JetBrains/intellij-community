// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.SpacePresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class InlayHintsPassFactoryTest : BasePlatformTestCase() {
  fun testAlwaysEnabledWorksAfterDisabling() {
    myFixture.configureByText("file.txt", "text")
    val language = PlainTextLanguage.INSTANCE
    val key = SettingsKey<NoSettings>("key")
    ExtensionTestUtil.maskExtensions(InlayHintsProviderFactory.EP, listOf(object : InlayHintsProviderFactory {
      override fun getProvidersInfo(project: Project): List<ProviderInfo<out Any>> {
        return listOf(ProviderInfo(language, dummyProvider(key, object : InlayHintsCollector {
          override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
            sink.addInlineElement(0, true, SpacePresentation(1, 1))
            return false
          }
        })))
      }
    }), testRootDisposable)
    InlayHintsSettings.instance().changeHintTypeStatus(key, language, false)
    InlayHintsPassFactory.setAlwaysEnabledHintsProviders(myFixture.editor, listOf(key))
    val factory = InlayHintsPassFactory()
    val pass = factory.createHighlightingPass(myFixture.file, myFixture.editor) as InlayHintsPass
    pass.doCollectInformation(EmptyProgressIndicator())
    pass.applyInformationToEditor()
    assertTrue(myFixture.editor.inlayModel.getInlineElementsInRange(0, 0).isNotEmpty())
  }

  private fun dummyProvider(key: SettingsKey<NoSettings>, collector: InlayHintsCollector): InlayHintsProvider<NoSettings> {
    return object : InlayHintsProvider<NoSettings> {
      override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector? {
        return collector
      }

      override fun createSettings(): NoSettings = NoSettings()

      override val name: String
        get() = throw NotImplementedError()
      override val key: SettingsKey<NoSettings> = key
      override val previewText: String?
        get() = throw NotImplementedError()

      override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
        throw NotImplementedError()
      }
    }
  }
}