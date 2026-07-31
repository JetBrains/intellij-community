// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import org.jetbrains.annotations.ApiStatus

/**
 * Frontend completion contributors ARE not supported YET.
 * This interface is an INTERNAL platform API NOT intended for use by plugins.
 *
 * @deprecated Use [CompletionContributor] instead and put your completion contributors in backend modules.
 */
@Deprecated("Use [CompletionContributor] instead and put your completion contributors in backend modules. " +
            "Frontend completion contributors ARE not supported yet, this interface is a platform implementation detail")
@ApiStatus.Internal
interface FrontendCompletionContributor