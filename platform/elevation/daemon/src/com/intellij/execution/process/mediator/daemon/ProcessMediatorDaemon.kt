// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

import io.grpc.ManagedChannel

interface ProcessMediatorDaemon {
  fun createChannel(): ManagedChannel = throw UnsupportedOperationException()
  fun stop()
  fun blockUntilShutdown()
}