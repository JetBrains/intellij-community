// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target

import com.intellij.openapi.fileChooser.FileChooserDescriptor

/**
 * Hints for [BrowsableTargetEnvironmentType.createBrowser].
 *
 * [showLocalFsInBrowser]: some targets (WSL is the only known for now) may provide access to the local filesystem.
 *   This flag allows such access, hence user could choose a local path on the target (like ``/mnt/c`` for WSL).
 *   For other targets this flag might be ignored.
 *
 * [customFileChooserDescriptor] to browse files; it also might be ignored by some targets.
 **/
@Suppress("removal")
data class TargetBrowserHints @JvmOverloads constructor(
  val showLocalFsInBrowser: Boolean = true,
  val customFileChooserDescriptor: FileChooserDescriptor? = null,
)
