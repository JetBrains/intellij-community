// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Implement this interface in your [SearchScope] if you want to associate information about [CodeInsightContext]s and [VirtualFile]s
 * that are contained in this scope.
 *
 * Not all scopes require that. E.g., file-type- or any other restricting scopes do not need that.
 *
 * It makes sense to implement this interface if your scope encapsulates information about the project structure of your technology.
 */
// todo ijpl-339 mark experimental
@Internal
interface CodeInsightContextAwareSearchScope {
  /**
   * @return an object encapsulating information about [CodeInsightContext]s associated with the scope
   */
  val codeInsightContextInfo: CodeInsightContextInfo
}

@Internal
fun SearchScope.contains(file: VirtualFile, context: CodeInsightContext): Boolean {
  val info = this.codeInsightContextInfo
  return when (info) {
    is NoContextInformation -> this.contains(file)
    is ActualCodeInsightContextInfo -> info.contains(file, context)
  }
}

@Internal
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

// todo ijpl-339 does not support case when this scope contains a file in several contexts
@Internal
fun SearchScope.getAnyCorrespondingContext(file: VirtualFile): CodeInsightContext {
  val contextInfo = getFileContextInfo(file)
  return when (contextInfo) {
    is ActualContextFileInfo -> contextInfo.contexts.first()
    else -> anyContext()
  }
}

val SearchScope.codeInsightContextInfo: CodeInsightContextInfo
  @Internal
  get() = if (this is CodeInsightContextAwareSearchScope) this.codeInsightContextInfo else NoContextInformation()


/**
 * A base interface storing information about [CodeInsightContext]s associated with the [SearchScope] which returns this object
 */
// todo ijpl-339 mark experimental
@Internal
sealed interface CodeInsightContextInfo

/**
 * if [CodeInsightContextAwareSearchScope.codeInsightContextInfo] returns this object, it means the scope does not contain information
 * about scopes.
 *
 * Implementation note: we need this interface because not all scopes can know at compilation time if they are aware of contexts.
 * Examples of such scope are Union and Intersection scopes.
 */
// todo ijpl-339 mark experimental
@Internal
sealed interface NoContextInformation : CodeInsightContextInfo

/**
 * [CodeInsightContextAwareSearchScope.codeInsightContextInfo] can return this object.
 * If you get its instance, you can use it for checking if a given [VirtualFile] is associated with [CodeInsightContext]s within the [SearchScope].
 */
// todo ijpl-339 mark experimental
@Internal
sealed interface ActualCodeInsightContextInfo : CodeInsightContextInfo {
  /**
   * @return true if scope contains [file] with [context].
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
 * @see CodeInsightContextAwareSearchScope
 */
// todo ijpl-339 mark experimental
@Internal
sealed interface CodeInsightContextFileInfo

/**
 * File info indicating that the corresponding [SearchScope] does not contain the corresponding [VirtualFile].
 *
 * @see ActualCodeInsightContextInfo.getFileInfo
 */
// todo ijpl-339 mark experimental
@Internal
sealed interface DoesNotContainFileInfo : CodeInsightContextFileInfo

/**
 * File info indicating that the corresponding [SearchScope] contains the corresponding [VirtualFile], but
 * does not have information about contexts associated with this file.
 *
 * This is possible when you unite a scope aware of contexts and a scope unaware of contexts.
 *  todo ijpl-339 should this be possible??? Can we forbid this???
 *
 * @see ActualCodeInsightContextInfo.getFileInfo
 */
// todo ijpl-339 mark experimental
@Internal
sealed interface NoContextFileInfo : CodeInsightContextFileInfo

/**
 * File info indicating the [CodeInsightContext]s associated with the corresponding [VirtualFile]
 */
// todo ijpl-339 mark experimental
@Internal
sealed interface ActualContextFileInfo : CodeInsightContextFileInfo {
  val contexts: Collection<CodeInsightContext>
}

// todo ijpl-339 mark experimental
@Internal
fun NoContextInformation(): NoContextInformation = NoContextInformationImpl


// todo ijpl-339 mark experimental
@Internal
fun DoesNotContainFileInfo(): DoesNotContainFileInfo = DoesNotContainFileInfoImpl

// todo ijpl-339 mark experimental
@Internal
fun ActualContextFileInfo(contexts: Collection<CodeInsightContext>): ActualContextFileInfo {
  if (contexts.isEmpty()) {
    throw IllegalArgumentException("Contexts cannot be empty")
  }
  return ActualContextFileInfoImpl(contexts)
}

@Internal
fun createContainingContextFileInfo(contexts: Collection<CodeInsightContext>): CodeInsightContextFileInfo {
  if (contexts.isEmpty()) {
    return NoContextFileInfo()
  }
  else {
    return ActualContextFileInfo(contexts)
  }
}

// todo ijpl-339 mark experimental
@Internal
fun NoContextFileInfo(): NoContextFileInfo = NoContextFileInfoImpl

// -------------------------------- implementation --------------------------------


private object NoContextInformationImpl: NoContextInformation

private object DoesNotContainFileInfoImpl: DoesNotContainFileInfo

private class ActualContextFileInfoImpl(
  override val contexts: Collection<CodeInsightContext>
) : ActualContextFileInfo

private object NoContextFileInfoImpl: NoContextFileInfo