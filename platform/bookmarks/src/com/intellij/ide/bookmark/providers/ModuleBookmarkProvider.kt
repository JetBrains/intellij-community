// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.providers

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkProvider
import com.intellij.ide.projectView.impl.AbstractUrl
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil

internal class ModuleBookmarkProvider(private val project: Project) : BookmarkProvider {
  override fun getWeight(): Int = 200
  override fun getProject(): Project = project

  internal val moduleManager: ModuleManager?
    get() = if (project.isDisposed) null else ModuleManager.getInstance(project)

  internal val projectSettingsService: ProjectSettingsService?
    get() = if (project.isDisposed) null else ProjectSettingsService.getInstance(project)

  override fun compare(bookmark1: Bookmark, bookmark2: Bookmark): Int {
    bookmark1 as ModuleBookmark
    bookmark2 as ModuleBookmark
    if (bookmark1.isGroup && !bookmark2.isGroup) return -1
    if (!bookmark1.isGroup && bookmark2.isGroup) return +1
    return StringUtil.naturalCompare(bookmark1.name, bookmark2.name)
  }

  override fun createBookmark(map: MutableMap<String, @NlsSafe String>): ModuleBookmark? =
    map["group"] ?.let { ModuleBookmark(this, it, true) } ?: map["module"]?.let { ModuleBookmark(this, it, false) }

  private fun createGroupBookmark(name: @NlsSafe String?) = name?.let { ModuleBookmark(this, it, true) }
  private fun createModuleBookmark(name: @NlsSafe String?) = name?.let { ModuleBookmark(this, it, false) }
  override fun createBookmark(context: Any?): ModuleBookmark? = when (context) {
    is AbstractUrl  -> when (context.type) {
      AbstractUrl.TYPE_MODULE_GROUP -> createGroupBookmark(context.url)
      AbstractUrl.TYPE_MODULE -> createModuleBookmark(context.url)
      else -> null
    }
    is Module -> createModuleBookmark(context.name)
    else -> null
  }
}
