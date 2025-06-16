// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.hints.parameters

import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.codeInsight.hints.PARAMETER_NAME_HINTS_EP
import com.intellij.codeInsight.hints.getExcludeList
import com.intellij.codeInsight.hints.settings.Diff
import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import javax.swing.Icon

class ParameterHintsExcludeListServiceTest : LightPlatformTestCase() {

  fun `test inlay provider exclude list is included in dependent config provider`() {
    val langA = registerTestLanguageA()
    val langB = registerTestLanguageB(null)
    registerInlayProviders(langA, InlayProvider())
    registerConfigProviders(langB, ConfigProvider())

    val service = getService()
    assertTrue(service.isExcluded("a.contains", listOf("elem"), langB))

    // methods from Lang_A are also excluded for Lang_B, but not the other way around
    assertTrue(service.isExcluded("a.equals", listOf("other"), langB))
    assertFalse(service.isExcluded("a.contains", listOf("elem"), langA))

    assertSize(DefaultExcludeList_1.size + DefaultExcludeList_2.size, service.getFullExcludeList(langB))

    // updates to backing settings are reflected
    removePatterns(langA, "*.equals(*)")
    assertFalse(service.isExcluded("a.equals", listOf("other"), langB))
    assertFalse(service.isExcluded("a.equals", listOf("other"), langA))
    addPatterns(langA, "*.equals(*)")
    assertTrue(service.isExcluded("a.equals", listOf("other"), langB))
    assertTrue(service.isExcluded("a.equals", listOf("other"), langA))
  }

  fun `test exclude list is inherited from a base language`() {
    val langA = registerTestLanguageA()
    val langB = registerTestLanguageB(langA)
    registerInlayProviders(langA, InlayProvider())

    val service = getService()

    // Lang_C has Lang_A as a base language, so it inherits the exclude list from it
    assertEquals(service.getFullExcludeList(langB), service.getFullExcludeList(langA))
    val langCConfig = service.getConfig(langB)
    assertNotNull(langCConfig)
    assertEquals(langCConfig!!.language, langA)
  }

  private fun registerInlayProviders(language: Language, vararg providers: InlayParameterHintsProvider) {
    ExtensionTestUtil.addExtensions(
      PARAMETER_NAME_HINTS_EP,
      providers.toList().map { LanguageExtensionPoint(language.id,
                                                      it.javaClass.name,
                                                      pluginDescriptor) },
      testRootDisposable
    )
  }

  private fun registerConfigProviders(language: Language, vararg providers: ParameterHintsExcludeListConfigProvider) {
    ExtensionTestUtil.addExtensions(
      ParameterHintsExcludeListConfigProvider.EP_NAME,
      providers.toList().map { LanguageExtensionPoint(language.id,
                                                      it.javaClass.name,
                                                      pluginDescriptor) },
      testRootDisposable
    )
  }

  private fun registerTestLanguageA(): Language = registerTestLanguage(makeLanguageFileTypeA())
  private fun registerTestLanguageB(base: Language? = null): Language = registerTestLanguage(makeLanguageFileTypeB(base))

  private fun registerTestLanguage(fileType: LanguageFileType): Language {
    val manager = FileTypeManager.getInstance() as FileTypeManagerImpl
    manager.registerFileType(
      fileType,
      emptyList(),
      testRootDisposable,
      pluginDescriptor
    )
    return fileType.language
  }

  private val pluginDescriptor get() = PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)!!

  @Suppress("SameParameterValue")
  private fun removePatterns(language: Language, vararg patterns: String) {
    updateStoredPatterns(language) { it - patterns.toSet() }
  }

  @Suppress("SameParameterValue")
  private fun addPatterns(language: Language, vararg patterns: String) =
    updateStoredPatterns(language) { it + patterns.toSet() }

  private fun updateStoredPatterns(language: Language, update: (Set<String>) -> Set<String>) {
    val settings = getSettings()
    val config = getService().getConfig(language)!!
    val updated = update(getExcludeList(settings, config))
    settings.setExcludeListDiff(language, Diff.build(config.defaultExcludeList, updated))
  }

  private fun getService() = ParameterHintsExcludeListService.getInstance()

  private fun getSettings() = ParameterNameHintsSettings.getInstance()
}

private val DefaultExcludeList_1 = setOf(
  "*.equals(*)",
  "*.charAt(*)",
  "*.get(*)",
)

private val DefaultExcludeList_2 = setOf(
  "*.println(*)",
  "*.contains(*)",
  "*.endsWith(*)"
)

private const val LANG_A_ID = "Lang_A"
private const val LANG_B_ID = "Lang_B"

// We need as many of these functions as many different mock languages we want to use in a single test.
// This is because:
//  - The language class must be unique when registered.
//  - A new instance is needed for every test, since this registration happens inside the Language constructor.
private fun makeLanguageFileTypeA(base: Language? = null) =
  TestLanguageFileType(object : Language(base, LANG_A_ID) {})

private fun makeLanguageFileTypeB(base: Language? = null) =
  TestLanguageFileType(object : Language(base, LANG_B_ID) {})

private open class TestLanguageFileType(language: Language) : LanguageFileType(language) {
  override fun getName() = language.id
  override fun getDescription(): @NlsContexts.Label String = "test file type for ${language.id}"
  override fun getDefaultExtension(): @NlsSafe String = language.id
  override fun getIcon(): Icon? = null
}

private class InlayProvider : InlayParameterHintsProvider {
  override fun getDefaultBlackList() = DefaultExcludeList_1
  override fun getBlackListDependencyLanguage() = null
}

private class ConfigProvider : ParameterHintsExcludeListConfigProvider {
  override fun getDefaultExcludeList() = DefaultExcludeList_2
  override fun getExcludeListDependencyLanguage() = Language.findLanguageByID(LANG_A_ID)
}