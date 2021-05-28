// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.ide.impl.TrustChangeNotifier
import com.intellij.ide.impl.UntrustedProjectEditorNotificationPanel
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.ExternalResolverIsSafe.executesTrustedCodeOnly
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil.confirmLoadingUntrustedProject
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications

class UntrustedProjectNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel?>(), DumbAware {
  override fun getKey() = KEY

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    if (project.isTrusted()) {
      return null
    }
    val providers = collectUntrustedProjectModeProviders()
      .filter { it.shouldShowEditorNotification(project) }
    if (providers.isEmpty()) {
      return null
    }
    return UntrustedProjectEditorNotificationPanel(project, fileEditor) {
      if (confirmLoadingUntrustedProject(project, providers.map { it.systemId })) {
        for (provider in providers) {
          provider.loadAllLinkedProjects(project)
        }
      }
    }
  }

  private fun collectUntrustedProjectModeProviders(): Collection<UntrustedProjectModeProvider> {
    val providers = LinkedHashMap<ProjectSystemId, UntrustedProjectModeProvider>()
    ExternalSystemManager.EP_NAME.forEachExtensionSafe {
      providers[it.systemId] = ExternalSystemUntrustedProjectModeProvider(it)
    }
    EP_NAME.forEachExtensionSafe {
      if (it.systemId in providers) {
        LOG.warn("${it.javaClass.simpleName} for ${it.systemId} registered automatically")
      }
      providers[it.systemId] = it
    }
    return providers.values
  }

  companion object {
    private val EP_NAME = ExtensionPointName.create<UntrustedProjectModeProvider>("com.intellij.untrustedModeProvider")
    private val KEY = Key.create<EditorNotificationPanel?>("UntrustedProjectNotification")
    private val LOG = Logger.getInstance(UntrustedProjectNotificationProvider::class.java)
  }

  class TrustedListener : TrustChangeNotifier {
    override fun projectTrusted(project: Project) {
      EditorNotifications.getInstance(project).updateAllNotifications()
    }
  }

  private class ExternalSystemUntrustedProjectModeProvider(
    private val manager: ExternalSystemManager<*, *, *, *, *>
  ) : UntrustedProjectModeProvider {

    override val systemId = manager.systemId

    override fun shouldShowEditorNotification(project: Project): Boolean {
      val settings = manager.settingsProvider.`fun`(project)
      return !executesTrustedCodeOnly(systemId) && settings.linkedProjectsSettings.isNotEmpty()
    }

    override fun loadAllLinkedProjects(project: Project) {
      val settings = manager.settingsProvider.`fun`(project)
      for (linkedProjectSettings in settings.linkedProjectsSettings) {
        val externalProjectPath = linkedProjectSettings.externalProjectPath
        ExternalSystemUtil.refreshProject(externalProjectPath, ImportSpecBuilder(project, systemId))
      }
    }
  }
}