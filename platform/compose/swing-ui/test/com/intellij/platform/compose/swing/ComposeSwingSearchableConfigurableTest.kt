// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing

import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ex.ConfigurableCardPanel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import java.awt.BorderLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestApplication
class ComposeSwingSearchableConfigurableTest {

  private class Store {
    var flag: Boolean = false
  }

  private class TestConfigurable(store: Store) : ComposeSwingSearchableConfigurable() {
    val flag = bind(store::flag)

    override fun getId(): String = "test.compose.swing.configurable"
    override fun getDisplayName(): String = "Test"
  }

  @Test
  fun bindTracksModifiedAppliesAndResets() {
    val store = Store()
    val configurable = TestConfigurable(store)

    assertFalse(configurable.isModified())

    configurable.flag.value = true
    assertTrue(configurable.isModified(), "editing the bound state marks the page modified")

    configurable.apply()
    assertTrue(store.flag, "apply writes the state into the store")
    assertFalse(configurable.isModified())

    store.flag = false
    configurable.reset()
    assertFalse(configurable.flag.value, "reset reloads the state from the store")
    assertFalse(configurable.isModified())
  }

  /**
   * The Settings dialog decides what to put around a page from the runtime type of the component the page
   * hands it, and a page hosting a composition is not the type it looks for. What it settles on has to be
   * what a page written with `panel { }` gets, or the page stands differently from every page beside it.
   *
   * The margins have to reach the component the composition mounts rather than the host around it: a form
   * laid out by the platform grid steps a row that starts with a check box out to the left of the content
   * the other rows start at, and it can only do that inside the insets of the component it lays out. Margins
   * on the host leave it nowhere to step and stand the rest of the page a few pixels too far right - which
   * insets on the host cannot tell apart from margins that work, so this asks where they landed.
   *
   * The page mounts nothing until it reaches a window, which a test has no way to give it, so what mounting
   * does - a component arriving in the host - is done here instead.
   */
  @Test
  fun theMarginsOfAPageReachWhatTheCompositionMounts() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val gridPage = object : SearchableConfigurable {
      override fun getId(): String = "test.ui.dsl.configurable"
      override fun getDisplayName(): String = "Test"
      override fun createComponent(): JComponent = panel { row("Name:") { checkBox("On") } }
      override fun isModified(): Boolean = false
      override fun apply() {}
    }
    val plainPage = object : SearchableConfigurable {
      override fun getId(): String = "test.plain.configurable"
      override fun getDisplayName(): String = "Test"
      override fun createComponent(): JComponent = JPanel(BorderLayout())
      override fun isModified(): Boolean = false
      override fun apply() {}
    }

    val configurable = TestConfigurable(Store())
    try {
      val host = configurable.createComponent()
      assertEquals(JBUI.emptyInsets(), host.insets, "the host carries no margins of its own")

      val form = JPanel(GridLayout())
      host.add(form)
      assertEquals(insetsAroundPage(gridPage), form.insets, "a form laid out by the platform grid")

      val plain = JPanel(BorderLayout())
      host.add(plain)
      assertEquals(insetsAroundPage(plainPage), plain.insets, "content laid out by anything else")
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  /**
   * A page refuses a value by throwing from `apply`, which is what keeps the Settings dialog open and puts
   * the message in front of the user. The base collects the bindings; it must not stand between the page
   * and the dialog when the page refuses.
   */
  @Test
  fun aPageThatRefusesAValueSaysSoThroughApply() {
    val configurable = object : ComposeSwingSearchableConfigurable() {
      override fun getId(): String = "test.compose.swing.validation"
      override fun getDisplayName(): String = "Test"
      override fun apply() {
        super.apply()
        throw ConfigurationException("Port must be between 1024 and 65535")
      }
    }

    val refused = assertFailsWith<ConfigurationException> { configurable.apply() }
    assertEquals("Port must be between 1024 and 65535", refused.messageHtml.toString())
  }

  /** What the Settings dialog leaves around the page itself, once it has wrapped and bordered it. */
  private fun insetsAroundPage(configurable: Configurable): Insets {
    try {
      val component = checkNotNull(ConfigurableCardPanel.createConfigurableComponent(configurable))
      val page = generateSequence(component) { (it as? JScrollPane)?.viewport?.view as? JComponent }.last()
      return page.insets
    }
    finally {
      configurable.disposeUIResources()
    }
  }
}
