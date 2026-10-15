// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class EmptyHttpResponseException(requestName: String) : Exception("Request $requestName returned an unexpected empty response")
