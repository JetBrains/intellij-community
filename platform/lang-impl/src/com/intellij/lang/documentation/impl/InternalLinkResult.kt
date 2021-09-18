// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.impl

internal sealed class InternalLinkResult {

  object InvalidTarget : InternalLinkResult()

  object CannotResolve : InternalLinkResult()

  class OK(val request: DocumentationRequest) : InternalLinkResult()
}
