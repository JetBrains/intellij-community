// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.lang.IdeLanguageCustomization
import com.intellij.lang.Language
import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.GotoClassContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import com.intellij.psi.codeStyle.DisplayPriority
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
internal class IdeLanguageCustomizationTest {
  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `null languages are treated as non-primary`() {
    val primaryLanguage = object : Language("PrimaryLanguage") {}
    ApplicationManager.getApplication().registerOrReplaceServiceInstance(
      IdeLanguageCustomization::class.java,
      object : IdeLanguageCustomization() {
        override fun getPrimaryIdeLanguages(): List<Language> = java.util.List.of(primaryLanguage)
      },
      disposable,
    )
    ExtensionTestUtil.maskExtensions(
      ChooseByNameContributor.CLASS_EP_NAME,
      listOf(
        TestGotoClassContributor("fallback", null),
        TestGotoClassContributor("primary", primaryLanguage),
      ),
      disposable,
    )

    assertEquals("Primary", GotoClassPresentationUpdater.getActionTitle())
    assertEquals(listOf("primary", "fallback"), GotoClassPresentationUpdater.getElementKinds().toList())
    assertEquals(
      DisplayPriority.LANGUAGE_SETTINGS,
      object : CodeStyleSettingsProvider() {}.priority,
    )
  }

  private class TestGotoClassContributor(
    private val kind: String,
    private val language: Language?,
  ) : GotoClassContributor {
    override fun getElementKind(): String = kind

    override fun getElementLanguage(): Language? = language

    override fun getQualifiedName(item: NavigationItem): String? = null

    override fun getQualifiedNameSeparator(): String? = null

    override fun getNames(project: Project, includeNonProjectItems: Boolean): Array<String> = emptyArray()

    override fun getItemsByName(
      name: String,
      pattern: String,
      project: Project,
      includeNonProjectItems: Boolean,
    ): Array<NavigationItem> = emptyArray()
  }
}
