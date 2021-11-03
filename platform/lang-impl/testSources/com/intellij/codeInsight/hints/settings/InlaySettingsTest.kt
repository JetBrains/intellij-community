// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.hints.InlayHintsSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.lang.Language
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.containers.MultiMap

class InlaySettingsTest : LightPlatformTestCase() {

  fun testSettingsPersisted() {
    val settings = InlayHintsSettings()
    val language = object: Language("foobar1") {}
    data class ToPersist(var x: String, var y: Int) {
      @Suppress("unused")
      constructor() : this("", 0)
    }
    val key = SettingsKey<ToPersist>("foo")
    val saved = ToPersist("test", 42)
    settings.storeSettings(key, language, saved)
    settings.changeHintTypeStatus(key, language, false)
    settings.saveLastViewedProviderId("foo")
    val state = settings.state

    val newSettings = InlayHintsSettings()
    newSettings.loadState(state)
    val loaded = newSettings.findSettings(key, language) { ToPersist("asdasd", 23) }
    assertEquals(saved, loaded)
    assertFalse(newSettings.hintsEnabled(key, language))
    assertEquals("foo", newSettings.getLastViewedProviderId())
  }

  fun testLastProviderKey() {
    val settings = InlayHintsSettings()
    assertEquals(null, settings.getLastViewedProviderId())
    settings.saveLastViewedProviderId("k1")
    assertEquals("k1", settings.getLastViewedProviderId())
    settings.saveLastViewedProviderId("k2")
    assertEquals("k2", settings.getLastViewedProviderId())
  }

  fun testGlobalSwitch() {
    val settings = InlayHintsSettings()
    val language = Language.ANY
    assertTrue(settings.hintsEnabled(language)) // hints enabled by default
    settings.setEnabledGlobally(false)
    assertTrue(settings.hintsEnabled(language))
    assertFalse(settings.hintsShouldBeShown(language))
  }

  fun testLanguageSwitch() {
    val settings = InlayHintsSettings()
    val language = Language.ANY
    val key = SettingsKey<Any>("foo")
    assertTrue(settings.hintsEnabled(key, language))
    assertTrue(settings.hintsShouldBeShown(key, language))
    settings.setHintsEnabledForLanguage(language, false)
    assertTrue(settings.hintsEnabled(key, language))
    assertFalse(settings.hintsShouldBeShown(key, language))
  }

  fun testAllProviders() {
    val all = Language.getRegisteredLanguages().flatMap { lang: Language ->
      InlaySettingsProvider.EP.getExtensions().flatMap {
        it.createModels(project, lang).map { model -> Pair(model, lang) }
      }
    }

    val names = all.map { it.first.name }.toSortedSet().sortedByDescending { all.count { pair -> pair.first.name == it } }
    for (name in names) {
      val models = all.filter { pair -> pair.first.name == name }
      val s = "$name ${models.size} languages" + " (" + StringUtil.join(models.map { it.second.displayName }, ", ") + ")"
      println(s)
      val options = MultiMap<String, Language>()
      for (model in models.filter { it.first.cases.isNotEmpty() }) {
        options.putValue(StringUtil.join(model.first.cases.map { it.name }, ", "), model.second)
      }
      for (opt in options.keySet()) {
        val languages = StringUtil.join(options[opt].map { language -> language.displayName }, ", ")
        println("     Options for ${options[opt].size} languages ($languages): $opt")
      }
    }
  }
}