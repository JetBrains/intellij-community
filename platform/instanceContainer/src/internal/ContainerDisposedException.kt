// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

import kotlinx.coroutines.CancellationException

// TODO this should not be a CancellationException
//  If a coroutine requests something from a disposed container,
//  then it means that the coroutine is not a child of that container:
//  - either is should be actually a child,
//    in which case it should be cancelled and completed before container is disposed;
//  - or it should not be a child, in which case silently failing it with CE will lead to hard-to-debug bugs,
//    so it's better to throw non-CE instead.
class ContainerDisposedException internal constructor(
  containerName: String,
  trace: DisposalTrace
) : CancellationException("Container '$containerName' was disposed") {

  init {
    initCause(trace)
  }
}
