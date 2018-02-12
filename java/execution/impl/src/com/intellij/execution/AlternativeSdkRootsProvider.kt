// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution

import com.intellij.execution.configurations.ConfigurationWithAlternativeJre
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AppUIUtil
import java.util.*

/**
 * @author egor
 */
class AlternativeSdkRootsProvider : AdditionalLibraryRootsProvider() {
  override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
    if (Registry.`is`("index.run.configuration.jre")) {
      return RunManager.getInstance(project).allConfigurationsList
        .filterIsInstance(ConfigurationWithAlternativeJre::class.java)
        .filter { it.isAlternativeJrePathEnabled }
        .mapNotNull { it.alternativeJrePath }
        .mapNotNull { ProjectJdkTable.getInstance().findJdk(it) }
        .distinct()
        .map { SdkSyntheticLibrary(it) }
        .toList()
    }
    return emptyList()
  }

  class SdkSyntheticLibrary(val sdk: Sdk) : SyntheticLibrary() {
    override fun getSourceRoots(): Collection<VirtualFile> = sdk.rootProvider.getFiles(OrderRootType.SOURCES).toList()

    override fun getBinaryRoots(): Collection<VirtualFile> = sdk.rootProvider.getFiles(OrderRootType.CLASSES).toList()

    override fun equals(other: Any?) = other is SdkSyntheticLibrary && sourceRoots == other.sourceRoots && binaryRoots == other.binaryRoots

    override fun hashCode() = Objects.hash(sourceRoots, binaryRoots)
  }

  companion object {
    private val storedLibs = mutableListOf<SyntheticLibrary>()

    @JvmStatic
    fun reindexIfNeeded(project: Project) {
      if (!Registry.`is`("index.run.configuration.jre")) return
      val provider = AdditionalLibraryRootsProvider.EP_NAME.findExtension(AlternativeSdkRootsProvider::class.java)
      val additionalProjectLibraries = provider.getAdditionalProjectLibraries(project)
      if (additionalProjectLibraries != storedLibs) {
        storedLibs.clear()
        storedLibs.addAll(additionalProjectLibraries)
        AppUIUtil.invokeOnEdt {
          WriteAction.run<RuntimeException> {
            ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true)
          }
        }
      }
    }
  }
}