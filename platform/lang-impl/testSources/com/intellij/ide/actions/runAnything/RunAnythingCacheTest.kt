// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything

import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.ide.actions.runAnything.groups.RunAnythingCompletionGroup
import com.intellij.ide.actions.runAnything.items.RunAnythingItem
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.text.Matcher
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class RunAnythingCacheTest {
  companion object {
    val projectFixture = projectFixture()
  }

  @Test
  fun `test group visibility is saved and restored using title key`() {
    val cache = RunAnythingCache.getInstance(projectFixture.get())
    val group = createTestGroup("Test Group")

    assertTrue(cache.isGroupVisible(group), "Group should be visible by default")

    cache.saveGroupVisibilityKey(group, false)

    assertFalse(cache.isGroupVisible(group), "Group should be hidden after saving visibility as false")
  }

  private fun createTestGroup(title: String): RunAnythingCompletionGroup<String, RunAnythingProvider<String>> {
    val provider = object : RunAnythingProvider<String> {
      override fun getCompletionGroupTitle(): String = title
      override fun findMatchingValue(dataContext: DataContext, pattern: String): String? = null
      override fun getValues(dataContext: DataContext, pattern: String): Collection<String> = emptyList()
      override fun execute(dataContext: DataContext, value: String) {}
      override fun getCommand(value: String): String = value
      override fun getHelpItem(dataContext: DataContext): RunAnythingItem? = null
      override fun getAdText(): String? = null
      override fun getIcon(value: String) = null
      override fun getMainListItem(dataContext: DataContext, value: String): RunAnythingItem = throw UnsupportedOperationException()
      override fun getMatcher(dataContext: DataContext, pattern: String): Matcher? = null
      override fun getExecutionContexts(dataContext: DataContext): List<RunAnythingContext> = emptyList()
    }
    return RunAnythingCompletionGroup(provider)
  }
}
