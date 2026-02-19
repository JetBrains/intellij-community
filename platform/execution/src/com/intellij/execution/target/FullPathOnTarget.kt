// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target

import com.intellij.openapi.util.NlsSafe

/**
 * This is a path on target. String is temporary solution but would be changed to the real target-specific class
 */
typealias FullPathOnTarget = @NlsSafe String

fun FullPathOnTarget(path: String): FullPathOnTarget = path