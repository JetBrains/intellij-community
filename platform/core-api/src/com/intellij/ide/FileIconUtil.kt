// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable.IconFlags
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.Icon

private val LOG: Logger
  get() = logger<FileIconUtil>()

@Internal
object FileIconUtil {
  fun getIconFromProviders(file: VirtualFile, @IconFlags flags: Int, project: Project?): Icon? {
    for (extension in FileIconProvider.EP_NAME.filterableLazySequence()) {
      val icon = runCatching {
        extension.instance?.getIcon(file, flags, project)
      }.getOrHandleException {
        if (it !is IndexNotReadyException) {
          LOG.warn(PluginException("FileIconProvider $extension threw an exception", it, extension.pluginDescriptor.pluginId))
        }
      }
      if (icon != null) {
        return icon
      }
    }
    return null
  }

  fun patchIconByIconPatchers(icon: Icon, file: VirtualFile, flags: Int, project: Project?): Icon {
    var patched = icon
    for (extension in FileIconPatcher.EP_NAME.filterableLazySequence()) {
      patched = runCatching {
        extension.instance?.patchIcon(patched, file, flags, project)
      }.getOrHandleException {
        if (it !is IndexNotReadyException) {
          LOG.warn(PluginException("FileIconPatcher $extension threw an exception", it, extension.pluginDescriptor.pluginId))
        }
      } ?: patched
    }
    return patched
  }
}
