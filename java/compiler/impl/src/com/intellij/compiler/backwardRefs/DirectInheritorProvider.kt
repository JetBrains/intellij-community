// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.backwardRefs

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.jps.backwardRefs.NameEnumerator

/**
 * An interface for delivering missing parts of the class hierarchy to [JavaBackwardReferenceIndexReaderFactory.BackwardReferenceReader]
 */
@IntellijInternalApi
interface DirectInheritorProvider {
  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName.create<DirectInheritorProvider>("com.intellij.languageCompilerRefAdapter.directInheritorProvider")
  }

  fun findDirectInheritors(searchId: SearchId, nameEnumerator: NameEnumerator): Collection<CompilerRef.CompilerClassHierarchyElementDef>
}

/**
 * An interface provides missing information to [JavaBackwardReferenceIndexReaderFactory.BackwardReferenceReader.getHierarchy] for finding
 * classes not involved in Java compilation
 */
@IntellijInternalApi
interface SearchIdHolder {
  val searchId: SearchId
}