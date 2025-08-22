// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental
@file:JvmName("CodeInsightContextAwareSearchScopes")

package com.intellij.psi.search

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextManager
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import org.jetbrains.annotations.ApiStatus

/**
 * Implement this interface in your [SearchScope] if you want to associate information about [CodeInsightContext]s and [VirtualFile]s
 * that are contained in this scope.
 *
 * Not all scopes require that. E.g., file-type- or any other restricting scopes do not need that.
 *
 * It makes sense to implement this interface if your scope encapsulates information about the project structure of your technology.
 */
@ApiStatus.Experimental
interface CodeInsightContextAwareSearchScope {
  /**
   * @return an object encapsulating information about [CodeInsightContext]s associated with the scope
   */
  val codeInsightContextInfo: CodeInsightContextInfo
}

@ApiStatus.Experimental
fun SearchScope.contains(file: VirtualFile, context: CodeInsightContext): Boolean {
  val info = this.codeInsightContextInfo
  return when (info) {
    is NoContextInformation -> this.contains(file)
    is ActualCodeInsightContextInfo -> info.contains(file, context)
  }
}

@ApiStatus.Experimental
fun SearchScope.getFileContextInfo(file: VirtualFile): CodeInsightContextFileInfo {
  val info = this.codeInsightContextInfo
  return when (info) {
    is NoContextInformation -> {
      if (this.contains(file))
        NoContextFileInfo()
      else
        DoesNotContainFileInfo()
    }
    is ActualCodeInsightContextInfo -> info.getFileInfo(file)
  }
}

@ApiStatus.Experimental
fun SearchScope.getCorrespondingContexts(file: VirtualFile): Collection<CodeInsightContext> {
  return when (val contextInfo = getFileContextInfo(file)) {
    is ActualContextFileInfo -> contextInfo.contexts
    is NoContextFileInfo -> listOf(anyContext())
    else -> emptyList()
  }
}

val SearchScope.codeInsightContextInfo: CodeInsightContextInfo
  @ApiStatus.Experimental
  get() = if (this is CodeInsightContextAwareSearchScope) this.codeInsightContextInfo else NoContextInformation()


/**
 * A base interface storing information about [CodeInsightContext]s associated with the [SearchScope] which returns this object
 *
 * There are two sealed implementations of this interface:
 *  - [ActualCodeInsightContextInfo]
 *  - [NoContextInformation]
 */
@ApiStatus.Experimental
sealed interface CodeInsightContextInfo

/**
 * if [CodeInsightContextAwareSearchScope.codeInsightContextInfo] returns this object, it means the scope does not contain information
 * about scopes. Note that this scope still can contain files.
 *
 * Implementation note: we need this interface because not all scopes can know at compilation time if they are aware of contexts.
 * Examples of such a scope are Union and Intersection scopes. I.e., a union scope can contain scopes aware of contexts as well as scopes that are not aware of contexts.
 *
 * @see NoContextInformation() constructor function
 * @see codeInsightContextInfo
 * @see CodeInsightContextAwareSearchScope
 */
@ApiStatus.Experimental
sealed interface NoContextInformation : CodeInsightContextInfo

/**
 * [CodeInsightContextAwareSearchScope.codeInsightContextInfo] can return this object.
 * If you get its instance, you can use it for checking if a given [VirtualFile] is associated with [CodeInsightContext]s within the [SearchScope].
 *
 * E.g., a scope representing a module's sources contains a source files in the scope of this module and does not contain this source files in the scope of other module.
 *
 * @see ActualCodeInsightContextInfo() constructor function
 * @see codeInsightContextInfo
 * @see CodeInsightContextAwareSearchScope
 */
@ApiStatus.Experimental
sealed interface ActualCodeInsightContextInfo : CodeInsightContextInfo {
  /**
   * @return true if scope contains [file] in the context of [context].
   */
  fun contains(file: VirtualFile, context: CodeInsightContext): Boolean

  /**
   * @return an object encapsulating the information about [CodeInsightContext]s associated with [file]
   */
  fun getFileInfo(file: VirtualFile): CodeInsightContextFileInfo
}

/**
 * A base interface encapsulating information about [VirtualFile] and [CodeInsightContext] association within a given [SearchScope].
 *
 * There are three sealed implementations of this interface:
 *  - [ActualContextFileInfo]   - the corresponding [SearchScope] CONTAINS the corresponding [VirtualFile] and contains information about contexts associated with this file.
 *  - [DoesNotContainFileInfo]  - the corresponding [SearchScope] DOES NOT CONTAIN the corresponding [VirtualFile]
 *  - [NoContextFileInfo]       - the corresponding [SearchScope] DOES NOT HAVE INFORMATION about contexts associated with this file.
 *
 * @see CodeInsightContextAwareSearchScope
 * @see ActualContextFileInfo
 * @see DoesNotContainFileInfo
 * @see NoContextFileInfo
 */
@ApiStatus.Experimental
sealed interface CodeInsightContextFileInfo

/**
 * File info indicating that the corresponding [SearchScope] does not contain the corresponding [VirtualFile].
 *
 * @see DoesNotContainFileInfo() constructor function
 * @see ActualCodeInsightContextInfo.getFileInfo
 * @see codeInsightContextInfo
 */
@ApiStatus.Experimental
sealed interface DoesNotContainFileInfo : CodeInsightContextFileInfo

/**
 * File info indicating that the corresponding [SearchScope] contains the corresponding [VirtualFile], but
 * does not have information about contexts associated with this file.
 *
 * This is possible when you unite a scope aware of contexts and a scope unaware of contexts.
 *  todo IJPL-339 should this be possible??? Can we forbid this???
 *
 * @see NoContextFileInfo() constructor function
 * @see codeInsightContextInfo
 */
@ApiStatus.Experimental
sealed interface NoContextFileInfo : CodeInsightContextFileInfo

/**
 * File info indicating the [CodeInsightContext]s associated with the corresponding [VirtualFile]
 *
 * @see ActualContextFileInfo() constructor function
 * @see codeInsightContextInfo
 */
@ApiStatus.Experimental
sealed interface ActualContextFileInfo : CodeInsightContextFileInfo {
  val contexts: Collection<CodeInsightContext>
}

/**
 * Constructs a [NoContextInformation] object
 *
 * @see CodeInsightContextFileInfo
 */
@ApiStatus.Experimental
fun NoContextInformation(): NoContextInformation = NoContextInformationImpl

/**
 * Constructs a [DoesNotContainFileInfo] object
 *
 * @see CodeInsightContextFileInfo
 */
@ApiStatus.Experimental
fun DoesNotContainFileInfo(): DoesNotContainFileInfo = DoesNotContainFileInfoImpl

/**
 * Constructs an [ActualContextFileInfo] object.
 * The passed [contexts] must not be empty.
 *
 * @see CodeInsightContextFileInfo
 */
@ApiStatus.Experimental
fun ActualContextFileInfo(contexts: Collection<CodeInsightContext>): ActualContextFileInfo {
  if (contexts.isEmpty()) {
    throw IllegalArgumentException("Contexts cannot be empty")
  }
  return ActualContextFileInfoImpl(contexts)
}

/**
 * Constructs an [CodeInsightContextFileInfo] with the given [contexts].
 * If the [contexts] are empty, then [DoesNotContainFileInfo] is returned.
 * Otherwise, [ActualContextFileInfo] is returned.
 *
 * @see CodeInsightContextFileInfo
 */
@ApiStatus.Experimental
fun createContainingContextFileInfo(contexts: Collection<CodeInsightContext>): CodeInsightContextFileInfo {
  if (contexts.isEmpty()) {
    return NoContextFileInfo()
  }
  else {
    return ActualContextFileInfo(contexts)
  }
}

/**
 * Constructs a [NoContextFileInfo] object.
 *
 * @see CodeInsightContextFileInfo
 */
@ApiStatus.Experimental
fun NoContextFileInfo(): NoContextFileInfo = NoContextFileInfoImpl

/**
 * @param file must be unwrapped by [com.intellij.notebook.editor.BackedVirtualFile.getOriginFileIfBacked] already
 */
@ApiStatus.Internal
fun tryCheckingFileInScope(
  file: VirtualFile,
  viewProvider: FileViewProvider,
  globalScope: GlobalSearchScope,
  project: Project,
): Boolean {
  val contextManager = CodeInsightContextManager.getInstance(project)
  val cachedContext = contextManager.getCodeInsightContextRaw(viewProvider)
  if (cachedContext !== anyContext()) {
    // the file has an assigned context, so we can check it right away
    return globalScope.contains(file, cachedContext)
  }

  // By default, the file does not have an assigned context before the context is really requested.
  // And once we request it, it gets stuck to the file.
  // So let's try assigning it to the context which the scope wants to avoid building addition psi
  when (val contextInfo = globalScope.getFileContextInfo(file)) {
    is ActualContextFileInfo -> {
      val context = contextInfo.contexts.first()
      val actualCodeInsightContext = contextManager.getOrSetContext(viewProvider, context)
      if (actualCodeInsightContext === context) {
        return true
      }
      else {
        return globalScope.contains(file, actualCodeInsightContext)
      }
    }
    is NoContextFileInfo -> {
      // scope is not aware of contexts, so we don't care as well.
      return globalScope.contains(file)
    }
    is DoesNotContainFileInfo -> {
      return false
    }
  }
}

// -------------------------------- implementation --------------------------------


private object NoContextInformationImpl : NoContextInformation

private object DoesNotContainFileInfoImpl : DoesNotContainFileInfo

private class ActualContextFileInfoImpl(
  override val contexts: Collection<CodeInsightContext>,
) : ActualContextFileInfo

private object NoContextFileInfoImpl : NoContextFileInfo