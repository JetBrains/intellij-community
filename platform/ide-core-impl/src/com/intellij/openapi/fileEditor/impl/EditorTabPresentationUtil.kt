// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.lazyDumbAwareExtensions
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus.Experimental
import java.awt.Color
import java.util.concurrent.CancellationException

object EditorTabPresentationUtil {
  @JvmStatic
  fun getEditorTabTitle(project: Project, file: VirtualFile): @NlsContexts.TabTitle String {
    return getCustomEditorTabTitle(project, file)
           ?: doGetUniqueNameEditorTabTitle(project = project, file = file)
           ?: file.presentableName
  }

  @Experimental
  suspend fun getEditorTabTitleAsync(project: Project, file: VirtualFile): @NlsContexts.TabTitle String {
    getCustomEditorTabTitleAsync(project, file)?.let {
      return it
    }
    return getUniqueNameEditorTabTitleAsync(project = project, file = file) ?: file.presentableName
  }

  @JvmStatic
  fun getCustomEditorTabTitle(project: Project, file: VirtualFile): @NlsContexts.TabTitle String? {
    for (provider in EditorTabTitleProvider.EP_NAME.lazySequence()) {
      val result = try {
        provider.getEditorTabTitle(project, file)
      }
      catch (_: IndexNotReadyException) {
        continue
      }

      if (!result.isNullOrEmpty()) {
        return result
      }
    }
    return null
  }

  @Experimental
  suspend fun getCustomEditorTabTitleAsync(project: Project, file: VirtualFile): @NlsContexts.TabTitle String? {
    for (extension in EditorTabTitleProvider.EP_NAME.filterableLazySequence()) {
      val provider = extension.instance ?: continue
      val result = try {
        provider.getEditorTabTitleAsync(project, file)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        thisLogger().error(PluginException(e, extension.pluginDescriptor.pluginId))
        continue
      }

      if (!result.isNullOrEmpty()) {
        return result
      }
    }
    return null
  }

  @JvmStatic
  fun getUniqueEditorTabTitle(project: Project, file: VirtualFile): String {
    val name = getEditorTabTitle(project, file)
    return if (name == file.presentableName) UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(project, file) else name
  }

  @JvmStatic
  fun getEditorTabBackgroundColor(project: Project, file: VirtualFile): Color? {
    for (provider in EditorTabColorProvider.EP_NAME.lazyDumbAwareExtensions(project)) {
      provider.getEditorTabColor(project, file)?.let { return it }
    }
    return null
  }

  @JvmStatic
  fun getFileBackgroundColor(project: Project, file: VirtualFile): Color? {
    for (provider in EditorTabColorProvider.EP_NAME.lazyDumbAwareExtensions(project)) {
      provider.getProjectViewColor(project, file)?.let { return it }
    }
    return null
  }
}