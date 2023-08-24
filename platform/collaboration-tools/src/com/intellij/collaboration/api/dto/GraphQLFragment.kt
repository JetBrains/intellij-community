// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.api.dto

/**
 * Informational/marker annotation to link classes to fragment files
 */
@Repeatable
annotation class GraphQLFragment(val filePath: String)