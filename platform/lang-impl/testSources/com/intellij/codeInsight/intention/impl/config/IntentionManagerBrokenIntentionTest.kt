// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionBean
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A registered intention whose class does not load must not abort [IntentionManager.getAvailableIntentions].
 * The manager reports the error and returns the other intentions.
 */
@TestApplication
internal class IntentionManagerBrokenIntentionTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `an intention whose class fails to load does not hide the other intentions`() {
    val bean = IntentionActionBean()
    bean.className = "com.intellij.codeInsight.intention.impl.config.NoSuchIntention"
    val descriptor = DefaultPluginDescriptor(PluginId.getId("com.intellij.intention.brokenIntentionTest"), javaClass.classLoader)
    bean.pluginDescriptor = descriptor
    IntentionManagerImpl.EP_INTENTION_ACTIONS.point.registerExtension(bean, descriptor, disposable)

    var intentions: List<IntentionAction> = emptyList()
    val error = LoggedErrorProcessor.executeAndReturnLoggedError {
      intentions = IntentionManager.getInstance().availableIntentions
    }

    assertThat(error).isInstanceOf(PluginException::class.java)
    assertThat(error).hasRootCauseInstanceOf(ClassNotFoundException::class.java)
    assertThat(intentions).isNotEmpty()
    assertThat(intentions.filterIsInstance<IntentionActionWrapper>().map { it.implementationClassName }).doesNotContain(bean.className)
  }
}
