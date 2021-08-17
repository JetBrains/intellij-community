// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.build.BuildProgressListener
import java.io.Closeable
import java.util.function.Consumer

interface ExternalSystemOutputMessageDispatcher : Closeable, Appendable, BuildProgressListener {
  var stdOut: Boolean
  fun invokeOnCompletion(handler: Consumer<in Throwable?>)
}