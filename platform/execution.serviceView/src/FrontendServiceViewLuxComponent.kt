// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import org.jetbrains.annotations.ApiStatus

// A marker interface to identify entirely LUXed panels that need to be treated differently,
// e.g. no frontend customisations that rely on UI traversal of the component like changing actions or adding toolbars should be done
@ApiStatus.Internal
interface FrontendServiceViewLuxComponent