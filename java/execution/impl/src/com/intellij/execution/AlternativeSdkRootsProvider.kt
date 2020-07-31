// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution

import com.intellij.execution.configurations.ConfigurationWithAlternativeJre
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.JavaSyntheticLibrary
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AppUIUtil

class AlternativeSdkRootsProvider : AdditionalLibraryRootsProvider() {
  override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
    if (Registry.`is`("index.run.configuration.jre")) {
      return RunManager.getInstance(project).allConfigurationsList
        .asSequence()
        .filterIsInstance(ConfigurationWithAlternativeJre::class.java)
        .filter { it.isAlternativeJrePathEnabled }
        .mapNotNull { it.alternativeJrePath }
        .mapNotNull { ProjectJdkTable.getInstance().findJdk(it) }
        .distinct()
        .map { createSdkLibrary(it) }
        .toList()
    }
    return emptyList()
  }

  private fun createSdkLibrary(sdk: Sdk): JavaSyntheticLibrary {
    return JavaSyntheticLibrary(sdk.rootProvider.getFiles(OrderRootType.SOURCES).toList(),
                                sdk.rootProvider.getFiles(OrderRootType.CLASSES).toList(),
                                emptySet<VirtualFile>(),
                                null)
  }

  companion object {
    private val ALTERNATIVE_SDK_LIBS_KEY = Key.create<Collection<SyntheticLibrary>>("ALTERNATIVE_SDK_LIBS_KEY")

    @JvmStatic
    fun reindexIfNeeded(project: Project) {
      if (!Registry.`is`("index.run.configuration.jre")) return
      val provider = EP_NAME.findExtension(AlternativeSdkRootsProvider::class.java)!!
      val additionalProjectLibraries = provider.getAdditionalProjectLibraries(project)
      val update = synchronized(ALTERNATIVE_SDK_LIBS_KEY) {
        val res = additionalProjectLibraries != project.getUserData(ALTERNATIVE_SDK_LIBS_KEY)
        if (res) {
          project.putUserData(ALTERNATIVE_SDK_LIBS_KEY, additionalProjectLibraries)
        }
        res
      }
      if (update) {
        AppUIUtil.invokeOnEdt {
          WriteAction.run<RuntimeException> {
            ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true)
          }
        }
      }
    }
  }
}