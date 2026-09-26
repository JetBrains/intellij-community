// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.UI
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import javax.swing.JComponent
import javax.swing.JPanel

@TestApplication
internal class SearchEverywhereToolbarFieldsTest {
  private val registeredFields = ArrayList<SearchEverywhereToolbarField>()

  @AfterEach
  fun tearDown() {
    registeredFields.forEach(SearchEverywhereToolbarFields::unregister)
  }

  @Test
  fun eventGetsTheFieldOfItsWindow(): Unit = timeoutRunBlocking {
    val field = registerField(available = true)

    // A data context with a component is a UI data context. It must be created and read on the UI thread.
    val (isSameEvent, fieldInResult) = withContext(Dispatchers.UI) {
      val event = event(SimpleDataContext.getSimpleContext(PlatformDataKeys.CONTEXT_COMPONENT, JPanel()))
      val result = SearchEverywhereToolbarFields.withToolbarField(event)
      (result === event) to result.getData(SearchEverywhereToolbarField.DATA_KEY)
    }

    assertThat(isSameEvent).isFalse()
    assertThat(fieldInResult).isSameAs(field)
  }

  @Test
  fun unavailableFieldIsSkipped() {
    registerField(available = false)
    val event = event()

    val result = SearchEverywhereToolbarFields.withToolbarField(event)

    assertThat(result).isSameAs(event)
    assertThat(result.getData(SearchEverywhereToolbarField.DATA_KEY)).isNull()
  }

  @Test
  fun fieldFromTheContextIsKept() {
    registerField(available = true)
    val contextField = registerField(available = true)
    val event = event(SimpleDataContext.getSimpleContext(SearchEverywhereToolbarField.DATA_KEY, contextField))

    val result = SearchEverywhereToolbarFields.withToolbarField(event)

    assertThat(result).isSameAs(event)
    assertThat(result.getData(SearchEverywhereToolbarField.DATA_KEY)).isSameAs(contextField)
  }

  @Test
  fun unregisteredFieldIsNotFound() {
    val field = registerField(available = true)
    assertThat(SearchEverywhereToolbarFields.findIn(null)).isSameAs(field)

    SearchEverywhereToolbarFields.unregister(field)

    assertThat(SearchEverywhereToolbarFields.findIn(null)).isNull()
  }

  private fun registerField(available: Boolean): SearchEverywhereToolbarField {
    val field = object : SearchEverywhereToolbarField {
      override val component: JComponent = JPanel()
      override val isAvailable: Boolean
        get() = available
    }
    SearchEverywhereToolbarFields.register(field)
    registeredFields.add(field)
    return field
  }

  private fun event(dataContext: DataContext = DataContext.EMPTY_CONTEXT): AnActionEvent {
    return AnActionEvent.createEvent(dataContext, Presentation(), ActionPlaces.KEYBOARD_SHORTCUT, ActionUiKind.NONE, null)
  }
}
