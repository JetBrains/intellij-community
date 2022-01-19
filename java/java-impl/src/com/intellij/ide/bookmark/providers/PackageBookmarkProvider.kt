// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.providers

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkProvider
import com.intellij.ide.projectView.impl.nodes.PackageElement
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade

internal class PackageBookmarkProvider(private val project: Project) : BookmarkProvider {
  override fun getWeight() = 100
  override fun getProject() = project

  internal val moduleManager
    get() = if (project.isDisposed) null else ModuleManager.getInstance(project)

  internal val projectSettingsService
    get() = if (project.isDisposed) null else ProjectSettingsService.getInstance(project)

  override fun compare(bookmark1: Bookmark, bookmark2: Bookmark): Int {
    bookmark1 as PackageBookmark
    bookmark2 as PackageBookmark
    StringUtil.naturalCompare(bookmark1.name, bookmark2.name).let { if (it != 0) return it }
    StringUtil.naturalCompare(bookmark1.module, bookmark2.module).let { if (it != 0) return it }
    if (bookmark1.library && !bookmark2.library) return +1
    if (!bookmark1.library && bookmark2.library) return -1
    return 0
  }

  override fun createBookmark(map: MutableMap<String, String>): PackageBookmark? {
    val psi = map["package"]?.let { JavaPsiFacade.getInstance(project).findPackage(it) } ?: return null
    val module = map["module"]?.let { ModuleManager.getInstance(project).findModuleByName(it) }
    return PackageBookmark(this, PackageElement(module, psi, map["library"].toBoolean()))
  }

  override fun createBookmark(context: Any?): PackageBookmark? = when (context) {
    is PackageElement -> PackageBookmark(this, context)
    else -> null
  }
}
