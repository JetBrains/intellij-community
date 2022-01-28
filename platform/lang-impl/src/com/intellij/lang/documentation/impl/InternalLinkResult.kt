// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.impl

import com.intellij.lang.documentation.ContentUpdater
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed class InternalLinkResult {

  object InvalidTarget : InternalLinkResult()

  object CannotResolve : InternalLinkResult()

  class Request(val request: DocumentationRequest) : InternalLinkResult()

  class Updater(val updater: ContentUpdater) : InternalLinkResult()
}
