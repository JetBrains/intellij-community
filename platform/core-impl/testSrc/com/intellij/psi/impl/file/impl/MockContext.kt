// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext

/** A [CodeInsightContext] that only holds a name, so that a test can tell two contexts apart. */
internal data class MockContext(val name: String) : CodeInsightContext
