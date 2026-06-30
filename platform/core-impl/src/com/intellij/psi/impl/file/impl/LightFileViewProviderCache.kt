// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.defaultContext
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiInvalidElementAccessException
import com.intellij.psi.impl.file.impl.FileManagerImpl.markInvalidated
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.addIfNotNull
import java.util.Collections
import java.util.function.Consumer

internal class LightFileViewProviderCache(
  val newFileViewProviderFactory: NewFileViewProviderFactory,
  val project: Project,
) : FileViewProviderCache {
  private val myPsiHardRefKey = Key.create<FileViewProvider?>("HARD_REFERENCE_TO_PSI") //non-static!

  private val myLightVirtualFiles: MutableSet<LightVirtualFile> = Collections.synchronizedSet(ContainerUtil.createWeakSet())
  private val myTempProviderStorage = createTemporaryProviderStorage()
  private val evaluator: ValidityEvaluator = ValidityEvaluatorImpl(myTempProviderStorage, this, newFileViewProviderFactory)

  override fun getRaw(
    file: VirtualFile,
    context: CodeInsightContext,
  ): FileViewProvider? =
    file.getUserData(myPsiHardRefKey)

  override fun getAndReanimateIfNecessary(
    vFile: VirtualFile,
    context: CodeInsightContext,
  ): FileViewProvider? {
    val provider = getRaw(vFile, context) ?: return null
    return evaluator.reanimateProviderIfNecessary(vFile, provider)
  }

  override fun cacheOrGet(
    file: VirtualFile,
    context: CodeInsightContext,
    provider: FileViewProvider,
  ): FileViewProvider =
    @Suppress("NULLABILITY_MISMATCH_BASED_ON_EXPLICIT_TYPE_ARGUMENTS_FOR_JAVA")
    file.putUserDataIfAbsent(myPsiHardRefKey, provider)

  override fun getAllProvidersRaw(vFile: VirtualFile): List<FileViewProvider> =
    listOfNotNull(vFile.getUserData(myPsiHardRefKey))

  override fun getAllProvidersAndReanimateIfNecessary(vFile: VirtualFile): List<FileViewProvider> =
    listOfNotNull(getAndReanimateIfNecessary(vFile, defaultContext()))

  override fun remove(file: VirtualFile): Iterable<FileViewProvider>? =
    file.removeUserData(myPsiHardRefKey)?.let { listOf(it) }

  override fun remove(
    file: VirtualFile,
    context: CodeInsightContext,
    viewProvider: AbstractFileViewProvider,
  ): Boolean =
    file.removeUserData(myPsiHardRefKey) != null

  override fun removeAllFileViewProvidersAndSet(
    vFile: VirtualFile,
    viewProvider: FileViewProvider,
  ) {
    myLightVirtualFiles.add(vFile as LightVirtualFile)
    checkLightFileHasNoOtherPsi(vFile)
    vFile.putUserData(myPsiHardRefKey, viewProvider)
  }

  override fun clear() {
    myLightVirtualFiles.forEach { file ->
      val viewProvider = file.getUserData(myPsiHardRefKey)
      if (viewProvider != null) {
        markInvalidated(viewProvider, this)
      }
    }
    myLightVirtualFiles.clear()
  }

  override fun forEachKey(block: Consumer<VirtualFile>) = throw UnsupportedOperationException()

  override fun forEach(block: FileViewProviderCache.CacheEntryConsumer) = throw UnsupportedOperationException()

  override fun processQueue() = throw UnsupportedOperationException()

  override fun trySetContext(
    viewProvider: FileViewProvider,
    context: CodeInsightContext,
  ): CodeInsightContext = throw UnsupportedOperationException()

  override fun markPossiblyInvalidated() = throw UnsupportedOperationException()

  override fun evaluateValidity(viewProvider: AbstractFileViewProvider): Boolean =
    evaluator.evaluateValidity(viewProvider)

  override fun findViewProvider(
    vFile: VirtualFile,
    context: CodeInsightContext,
  ): FileViewProvider {
    myLightVirtualFiles.add(vFile as LightVirtualFile)

    getAndReanimateIfNecessary(vFile, context)?.let {
      return it
    }

    myTempProviderStorage.get(vFile)?.let {
      return it
    }

    val viewProvider = newFileViewProviderFactory.createNewFileViewProvider(vFile, context)

    checkLightFileHasNoOtherPsi(vFile)
    return cacheOrGet(vFile, context, viewProvider)
  }

  private fun checkLightFileHasNoOtherPsi(vFile: VirtualFile) {
    val viewProvider = FileDocumentManager.getInstance().findCachedPsiInAnyProject(vFile) ?: return
    val otherProject = viewProvider.getManager().getProject()
    if (otherProject === this.project) return

    val psiFiles = viewProvider.getAllFiles().joinToString(separator = ", ") { f -> "${f.javaClass} [${f.getLanguage()}]" }

    val details = buildString {
      appendLine("existing = $viewProvider")
      appendLine("existing project  = $otherProject (disposed=${otherProject.isDisposed}, initialized=${otherProject.isInitialized})")
      appendLine("requested project = ${this@LightFileViewProviderCache.project} (disposed=${project.isDisposed}, initialized=${project.isInitialized})")
      appendLine("light file: class=${vFile.javaClass.name}, length=${vFile.length}, modStamp=${vFile.modificationStamp}, valid=${vFile.isValid}")
      appendLine("existing provider possiblyInvalidated=${viewProvider.isPossiblyInvalidated()}")
      appendLine("psiFiles: $psiFiles")
    }

    val conflictDetails = Attachment("lightFilePsiConflict.txt", details)
    val attachments = mutableListOf(conflictDetails)

    val creationTrace = when (val creationTrace = PsiInvalidElementAccessException.getCreationTrace(vFile)) {
      is Throwable -> Attachment("lightFileCreationTrace.txt", creationTrace)
      null -> null
      else -> Attachment("lightFileCreationTrace.txt", creationTrace.toString())
    }
    attachments.addIfNotNull(creationTrace)

    LOG.error(
      "Light files should have PSI only in one project: lightFile=${vFile.javaClass.name}, " +
      "existingProjectDisposed=${otherProject.isDisposed}, requestedProjectDisposed=${project.isDisposed}",
      *attachments.toTypedArray(),
    )
  }
}

private val LOG = logger<LightFileViewProviderCache>()