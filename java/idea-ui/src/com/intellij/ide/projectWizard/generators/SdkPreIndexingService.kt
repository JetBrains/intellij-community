// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.SystemProperties
import com.intellij.util.indexing.UnindexedFilesIndexer
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods.createIterators
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.IndexableSetContributorFilesIterator.Companion.createProjectUnAwareIndexableSetContributors
import it.unimi.dsi.fastutil.longs.LongSets

internal class SdkPreIndexingRequiredForSmartModeActivity: StartupActivity.RequiredForSmartMode {
  override fun runActivity(project: Project) {
    service<SdkPreIndexingService>().cancelCurrentPreIndexation()
  }
}

@Service
internal class SdkPreIndexingService: Disposable {
  private companion object {
    val isEnabled = SystemProperties.getBooleanProperty("sdk.pre.indexing", false)
  }

  @Volatile
  private var currentSdk: Sdk? = null

  @Volatile
  private var currentProgressIndicator: BackgroundableProcessIndicator? = null;

  init {
    ApplicationManager.getApplication().messageBus.connect().subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, object : ProjectJdkTable.Listener {
      override fun jdkRemoved(removedSdk: Sdk) {
        if (currentSdk == removedSdk) {
          cancelCurrentPreIndexation()
        }
      }
    })
  }

  @Synchronized
  fun requestPreIndexation(sdk: Sdk) {
    if (!isEnabled || !ConfigImportHelper.isFirstSession()) {
      return
    }
    if (sdk == currentSdk) {
      return
    }
    cancelCurrentPreIndexation()

    val defaultProject = DefaultProjectFactory.getInstance().defaultProject
    val providers = geSdkAndAdditionalSetIndexableFileProviders(sdk, defaultProject)

    val task = object : Task.Backgroundable(null, JavaUiBundle.message("project.wizard.sdk.preindexing.progress.title")) {
      override fun run(indicator: ProgressIndicator) {
        UnindexedFilesIndexer(defaultProject, providers, "SDK pre-indexing", LongSets.emptySet()).perform(indicator)
      }
    }

    currentSdk = sdk
    currentProgressIndicator = BackgroundableProcessIndicator(task)
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, currentProgressIndicator!!)
  }

  private fun geSdkAndAdditionalSetIndexableFileProviders(sdk: Sdk, project: Project): Map<IndexableFilesIterator, Collection<VirtualFile>> {
    val allIterators: MutableList<IndexableFilesIterator> = ArrayList(2)
    allIterators.addAll(createProjectUnAwareIndexableSetContributors())
    allIterators.addAll(createIterators(sdk))

    val providerToFiles = HashMap<IndexableFilesIterator, Collection<VirtualFile>>()
    for (iterator in allIterators) {
      val files: MutableSet<VirtualFile> = HashSet()
      iterator.iterateFiles(project, ContentIterator { fileOrDir: VirtualFile ->
        files.add(fileOrDir)
        true
      }, VirtualFileFilter.ALL)
      providerToFiles.put(iterator, files)
    }
    return providerToFiles
  }

  @Synchronized
  fun cancelCurrentPreIndexation() {
    currentSdk = null
    if (currentProgressIndicator != null) {
      currentProgressIndicator!!.cancel()
      currentProgressIndicator = null
    }
  }

  override fun dispose() {
    currentProgressIndicator?.cancel()
  }
}