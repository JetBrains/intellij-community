// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.ide.rpc

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * The version of a document that the frontend and the backend share in split mode.
 * Equal versions mean equal document texts.
 * Compare versions for equality only.
 * Send a version in an RPC call to tell which document state the offsets of the call are in.
 *
 * Use [patchVersion] to get the version of a document.
 */
@ApiStatus.Experimental
@Serializable
class DocumentPatchVersion @ApiStatus.Internal constructor(
  private val version: Int,
  private val hash: Long,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DocumentPatchVersion) return false
    return version == other.version && hash == other.hash
  }

  override fun hashCode(): Int = 31 * version + hash.hashCode()

  override fun toString(): String = "DocumentPatchVersion($version, $hash)"
}

/**
 * Returns the version of this document that the frontend and the backend share.
 * Returns `null` in the monolith and when the document is not synchronized.
 *
 * Call it on either side, in a read action or on the EDT.
 * Read the offsets that the version describes in the same read action.
 */
@ApiStatus.Experimental
fun Document.patchVersion(project: Project): DocumentPatchVersion? {
  return DocumentPatchVersionAccessor.getDocumentVersion(this, project)
}

/**
 * Creates a version for a test. Equal [value]s give equal versions.
 */
@ApiStatus.Experimental
@TestOnly
fun createDocumentPatchVersionForTests(value: Int): DocumentPatchVersion {
  check(ApplicationManager.getApplication().isUnitTestMode) { "Call createDocumentPatchVersionForTests only in tests" }
  return DocumentPatchVersion(value, value.toLong())
}

/**
 * Makes [patchVersion] return the result of [provider] for a test, until [parentDisposable] is disposed.
 * A `null` result falls back to the real version.
 */
@ApiStatus.Experimental
@TestOnly
fun maskDocumentPatchVersionsForTests(parentDisposable: Disposable, provider: (Document) -> DocumentPatchVersion?) {
  val accessor = object : DocumentPatchVersionAccessor {
    override fun getDocumentVersion(document: Document, project: Project): DocumentPatchVersion? = provider(document)
  }
  ApplicationManager.getApplication().extensionArea
    .getExtensionPoint<DocumentPatchVersionAccessor>(DocumentPatchVersionAccessor.EP_NAME.name)
    .registerExtension(accessor, LoadingOrder.FIRST, parentDisposable)
}
