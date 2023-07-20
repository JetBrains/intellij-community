// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.fileIndex

import com.intellij.workspaceModel.core.fileIndex.impl.JvmPackageRootDataInternal

/**
 * Implement this interface in your implementation of [com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData] if the root of the
 * corresponding file set is a JVM package and may contain *.class files or source files compiled to *.class files. This information will
 * be used by [com.intellij.openapi.roots.PackageIndex] to match package names to directories.
 */
interface JvmPackageRootData: JvmPackageRootDataInternal {
  override val packagePrefix: String
    get() = ""
}