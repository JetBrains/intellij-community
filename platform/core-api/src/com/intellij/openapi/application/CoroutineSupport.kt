// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.CoroutineContext

@Internal
interface CoroutineSupport {

  fun asContextElement(modalityState: ModalityState): CoroutineContext

  fun edtDispatcher(): CoroutineContext
}
