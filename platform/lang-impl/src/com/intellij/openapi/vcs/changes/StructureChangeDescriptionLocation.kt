// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import org.jetbrains.annotations.ApiStatus

/** Uses the Structure View text when no provider supplies a description for a structural change. */
@ApiStatus.Internal
class StructureChangeDescriptionLocation(private val presentableText: @NlsSafe String) : ElementDescriptionLocation() {
  override fun getDefaultProvider(): ElementDescriptionProvider = ElementDescriptionProvider { _, _ -> presentableText }
}
