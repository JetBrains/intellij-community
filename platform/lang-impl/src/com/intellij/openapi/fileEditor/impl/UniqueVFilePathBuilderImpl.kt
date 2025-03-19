// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.fileEditor.impl

import com.intellij.filename.UniqueNameBuilder
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.MultiMap
import com.intellij.util.indexing.DumbModeAccessType
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.IndexUpToDateCheckIn
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

private class UniqueVFilePathBuilderImpl : UniqueVFilePathBuilder() {
  override fun getUniqueVirtualFilePath(project: Project, file: VirtualFile, scope: GlobalSearchScope): String {
    return getUniqueVirtualFilePath(project = project, file = file, skipNonOpenedFiles = false, scope = scope)
  }

  override fun getUniqueVirtualFilePath(project: Project, vFile: VirtualFile): String {
    return getUniqueVirtualFilePath(project = project, file = vFile, scope = GlobalSearchScope.projectScope(project))
  }

  override fun getUniqueVirtualFilePathWithinOpenedFileEditors(project: Project, vFile: VirtualFile): String {
    return getUniqueVirtualFilePath(
      project = project,
      file = vFile,
      skipNonOpenedFiles = true,
      scope = GlobalSearchScope.projectScope(project),
    )
  }
}

private val shortNameBuilderCacheKey = Key.create<CachedValue<ConcurrentMap<GlobalSearchScope, ConcurrentMap<String, UniqueNameBuilder<VirtualFile>>>>>("project's.short.file.name.builder")
private val shortNameOpenedBuilderCacheKey = Key.create<CachedValue<ConcurrentMap<GlobalSearchScope, ConcurrentMap<String, UniqueNameBuilder<VirtualFile>>>>>("project's.short.file.name.opened.builder")
private val emptyBuilder = UniqueNameBuilder<VirtualFile>(/* root = */ null, /* separator = */ null)

private fun getName(file: VirtualFile): String = if (file is VirtualFilePathWrapper) file.presentableName else file.name

private fun getUniqueVirtualFilePath(
  project: Project,
  file: VirtualFile,
  skipNonOpenedFiles: Boolean,
  scope: GlobalSearchScope,
): String {
  val builder = getUniqueVirtualFileNameBuilder(
    project = project,
    file = file,
    skipNonOpenedFiles = skipNonOpenedFiles,
    scope = scope,
  ) ?: return getName(file)
  return builder.getShortPath(file)
}

private fun getUniqueVirtualFileNameBuilder(
  project: Project,
  file: VirtualFile,
  skipNonOpenedFiles: Boolean,
  scope: GlobalSearchScope,
): UniqueNameBuilder<VirtualFile>? {
  val key = if (skipNonOpenedFiles) shortNameOpenedBuilderCacheKey else shortNameBuilderCacheKey
  var data = project.getUserData(key)
  if (data == null) {
    data = CachedValuesManager.getManager(project).createCachedValue(
      {
        CachedValueProvider.Result(
          ConcurrentHashMap(2),
          DumbService.getInstance(project),
          getFilenameIndexModificationTracker(project),
          FileEditorManagerImpl.OPEN_FILE_SET_MODIFICATION_COUNT
        )
      }, false)
    project.putUserData(key, data)
  }

  val scopeToValueMap = data.value
  val valueMap = scopeToValueMap.computeIfAbsent(scope) { CollectionFactory.createConcurrentSoftValueMap() }
  val fileName = getName(file)
  var builder = valueMap.get(fileName)

  if (builder == null) {
    createAndCacheBuilders(
      project = project,
      requiredFile = file,
      valueMap = valueMap,
      skipNonOpenedFiles = skipNonOpenedFiles,
      scope = scope,
    )
    builder = valueMap.get(fileName)?.takeIf { it != emptyBuilder}
  }
  else if (builder == emptyBuilder) {
    builder = null
  }

  if (builder != null && builder.contains(file)) {
    return builder
  }

  return null
}

private fun getFilenameIndexModificationTracker(project: Project): ModificationTracker {
  if (FileBasedIndexExtension.USE_VFS_FOR_FILENAME_INDEX) {
    return ModificationTracker { FSRecords.getInstance().invertedNameIndexModCount }
  }
  return ModificationTracker {
    disableIndexUpToDateCheckInEdt<Long, RuntimeException> {
      @Suppress("removal", "DEPRECATION")
      FileBasedIndex.getInstance().getIndexModificationStamp(FilenameIndex.NAME, project)
    }
  }
}

private fun createAndCacheBuilders(
  project: Project,
  requiredFile: VirtualFile,
  valueMap: MutableMap<String?, UniqueNameBuilder<VirtualFile>?>,
  skipNonOpenedFiles: Boolean,
  scope: GlobalSearchScope,
) {
  val useIndex = !skipNonOpenedFiles && !LightEdit.owns(project)
  val openFiles = FileEditorManager.getInstance(project).openFiles
  val recentFiles = EditorHistoryManager.getInstance(project).fileList

  val multiMap = MultiMap.createSet<String, VirtualFile>()
  if (useIndex) {
    val names = (sequenceOf(requiredFile) + openFiles + recentFiles).mapTo(LinkedHashSet()) { getName(it) }
    val query = ThrowableComputable<Boolean, RuntimeException> {
      FilenameIndex.processFilesByNames(names, true, scope, null) { file ->
        val name = getName(file)
        // not-up-to-date index check
        if (names.contains(name)) {
          multiMap.putValue(name, file)
        }
        true
      }
    }
    if (isDumb(project)) {
      DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(query)
    }
    else {
      disableIndexUpToDateCheckInEdt(query)
    }
  }

  val requiredFileName = getName(requiredFile)
  for (file in openFiles.asSequence() + (if (skipNonOpenedFiles) emptyList() else recentFiles)) {
    if (requiredFileName == getName(file)) {
      multiMap.putValue(requiredFileName, file)
    }
  }

  for (fileName in multiMap.keySet()) {
    val files = multiMap.get(fileName)
    if (files.size < 2) {
      valueMap.put(fileName, emptyBuilder)
      continue
    }

    var path = project.basePath
    path = if (path == null) "" else FileUtilRt.toSystemIndependentName(path)
    val builder = UniqueNameBuilder<VirtualFile>(path, File.separator)
    for (virtualFile in files) {
      val presentablePath = if (virtualFile is VirtualFilePathWrapper) virtualFile.presentablePath else virtualFile.path
      builder.addPath(virtualFile, presentablePath)
    }
    valueMap.put(fileName, builder)
  }
}

@RequiresReadLock
private fun <T, E : Throwable?> disableIndexUpToDateCheckInEdt(computable: ThrowableComputable<T, E>): T {
  if (ApplicationManager.getApplication().isDispatchThread) {
    return IndexUpToDateCheckIn.disableUpToDateCheckIn(computable)
  }
  else {
    return computable.compute()
  }
}