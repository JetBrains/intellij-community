// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.documentation.impl

import com.intellij.platform.backend.documentation.ContentUpdater

sealed class InternalLinkResult {

  object InvalidTarget : InternalLinkResult()

  object CannotResolve : InternalLinkResult()

  class Request(val request: DocumentationRequest) : InternalLinkResult()

  class Updater(val updater: ContentUpdater) : InternalLinkResult()
}
