// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.newui.UiPluginManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapperPeer
import com.intellij.openapi.ui.DialogWrapperPeerFactory
import com.intellij.openapi.ui.impl.DialogWrapperPeerFactoryImpl
import com.intellij.openapi.ui.impl.DialogWrapperPeerImpl
import com.intellij.openapi.updateSettings.impl.InternalPluginResults
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.updateSettings.impl.PluginUpdateDialog
import com.intellij.openapi.updateSettings.impl.PluginUpdates
import com.intellij.openapi.updateSettings.impl.Product
import com.intellij.openapi.updateSettings.impl.UpdateCheckerFacade
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.loadPluginWithText
import com.intellij.platform.testFramework.plugins.dependsIntellijModulesLang
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.replaceService
import com.intellij.ui.UiInterceptors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

@TestApplication
internal class InstallPluginJbProtocolCommandTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val pluginsDir by tempPathFixture()
  private val pluginId = PluginId.getId("com.intellij.air")
  private val checker = TestUpdateCheckerFacade()

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking {
    val installedPlugin = withContext(Dispatchers.EDT) {
      loadPluginWithText(
        pluginSpec = plugin(pluginId.idString) {
          name = "Air"
          version = "1.0"
          dependsIntellijModulesLang()
        },
        pluginsDir = pluginsDir,
      )
    }
    Disposer.register(disposable, installedPlugin)
    val application = ApplicationManager.getApplication()
    application.replaceService(UpdateCheckerFacade::class.java, checker, disposable)
    application.replaceService(DialogWrapperPeerFactory::class.java, object : DialogWrapperPeerFactoryImpl() {
      override fun createPeer(
        wrapper: DialogWrapper,
        project: Project?,
        canBeParent: Boolean,
        ideModalityType: DialogWrapper.IdeModalityType,
      ): DialogWrapperPeer = object : DialogWrapperPeerImpl(wrapper, project, canBeParent, ideModalityType) {
        override fun isModal(): Boolean = true
      }
    }, disposable)
  }

  @Test
  fun `offer an update for an installed plugin`(): Unit = timeoutRunBlocking {
    val update = createUpdate()
    checker.onCheck = { plugins, indicator ->
      assertThat(plugins).containsExactly(pluginId)
      assertThat(ApplicationManager.getApplication().isDispatchThread).isFalse()
      assertThat(indicator).isNotNull().isSameAs(ProgressManager.getGlobalProgressIndicator())
      InternalPluginResults(PluginUpdates(allEnabled = listOf(update)))
    }
    var suggestedIds: List<PluginId>? = null
    UiInterceptors.registerPossible(disposable, object : UiInterceptors.UiInterceptor<PluginUpdateDialog>(PluginUpdateDialog::class.java) {
      override fun doIntercept(dialog: PluginUpdateDialog) {
        suggestedIds = dialog.selectedPluginModels.map { it.pluginId }
        dialog.doCancelAction()
      }
    })

    val result = JBProtocolCommand.execute("tool/plugin/install?id=${pluginId.idString}").message

    assertThat(result).isNull()
    assertThat(suggestedIds).containsExactly(pluginId)
  }

  @Test
  fun `offer an update reported for a disabled plugin`(): Unit = timeoutRunBlocking {
    checker.onCheck = { _, _ -> InternalPluginResults(PluginUpdates(allDisabled = listOf(createUpdate()))) }
    var dialogShown = false
    UiInterceptors.registerPossible(disposable, object : UiInterceptors.UiInterceptor<PluginUpdateDialog>(PluginUpdateDialog::class.java) {
      override fun doIntercept(dialog: PluginUpdateDialog) {
        dialogShown = true
        dialog.doCancelAction()
      }
    })

    val result = JBProtocolCommand.execute("tool/plugin/install?id=${pluginId.idString}").message

    assertThat(result).isNull()
    assertThat(dialogShown).isTrue()
  }

  @Test
  fun `keep the installed response when no update is available`(): Unit = timeoutRunBlocking {
    var checked = false
    checker.onCheck = { plugins, _ ->
      assertThat(plugins).containsExactly(pluginId)
      checked = true
      InternalPluginResults.empty()
    }

    val result = JBProtocolCommand.execute("tool/plugin/install?id=${pluginId.idString}").message

    assertThat(checked).isTrue()
    val name = UiPluginManager.getInstance().findInstalledPlugins(setOf(pluginId)).getValue(pluginId).name
    assertThat(result).isEqualTo(IdeBundle.message("jb.protocol.plugin.install.already.installed", name))
  }

  @Test
  fun `report an update check failure`(): Unit = timeoutRunBlocking {
    checker.onCheck = { _, _ ->
      InternalPluginResults(PluginUpdates(), errors = mapOf(null to IOException("Repository is unavailable")))
    }

    val result = JBProtocolCommand.execute("tool/plugin/install?id=${pluginId.idString}").message

    assertThat(result).isEqualTo(IdeBundle.message("updates.plugins.error.message1", "Repository is unavailable"))
  }

  @Test
  fun `cancel the update check`(): Unit = timeoutRunBlocking {
    checker.onCheck = { _, indicator ->
      indicator!!.cancel()
      indicator.checkCanceled()
      error("The update check must stop")
    }

    assertThrows<CancellationException> {
      JBProtocolCommand.execute("tool/plugin/install?id=${pluginId.idString}")
    }
  }

  private fun createUpdate(): PluginDownloader {
    val node = PluginNode(pluginId, "Air", "999.0").apply {
      downloadUrl = "https://plugins.example.test/update.zip"
    }
    return PluginDownloader.createDownloader(node, "https://plugins.example.test/updatePlugins.xml", null)
  }

  private class TestUpdateCheckerFacade : UpdateCheckerFacade {
    var onCheck: (Collection<PluginId>, ProgressIndicator?) -> InternalPluginResults = { _, _ ->
      error("Unexpected update check")
    }

    override fun getPluginUpdates(
      plugins: Collection<PluginId>,
      indicator: ProgressIndicator?,
      buildNumber: BuildNumber?,
    ): InternalPluginResults = onCheck(plugins, indicator)

    override val disabledToUpdate: Set<PluginId> = emptySet()

    override fun checkInstalledPluginUpdates(indicator: ProgressIndicator?, buildNumber: BuildNumber?): InternalPluginResults =
      error("The check must include only the requested plugins")

    override fun updateAndShowResult(): Unit = error("Unexpected update operation")
    override fun updateAndShowResult(project: Project?): Unit = error("Unexpected update operation")
    override fun getNotificationGroup() = error("Unexpected update operation")
    override fun getNotificationGroupForPluginUpdateResults() = error("Unexpected update operation")
    override fun getNotificationGroupForIdeUpdateResults() = error("Unexpected update operation")
    override fun loadProductData(indicator: ProgressIndicator?): Product = error("Unexpected update operation")
    override fun updateDescriptorsForInstalledPlugins(): Unit = error("Unexpected update operation")
    override fun saveDisabledToUpdatePlugins(): Unit = error("Unexpected update operation")
    override fun ignorePlugins(descriptors: List<IdeaPluginDescriptor>): Unit = error("Unexpected update operation")
  }
}
