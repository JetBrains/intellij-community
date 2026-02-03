// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bootstrap

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class InitAppContext(@JvmField val appRegistered: CompletableDeferred<Unit>, @JvmField val appLoaded: Job)