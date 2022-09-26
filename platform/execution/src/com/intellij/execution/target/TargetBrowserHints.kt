// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target

/**
 * Hints for [BrowsableTargetEnvironmentType.createBrowser]
 *  [showLocalFsInBrowser]: some targets (WSL is the only known for now) may provide access to the local filesystem.
 *  This flag allows such access, hence user could choose local path on target (like ``/mnt/c`` for WSL).
 *  For other targets this flag might be ignored
 **/
data class TargetBrowserHints(val showLocalFsInBrowser: Boolean = true)