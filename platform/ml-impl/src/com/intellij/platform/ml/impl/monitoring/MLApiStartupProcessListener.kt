// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.monitoring

import com.intellij.platform.ml.impl.MLTaskApproach
import com.intellij.platform.ml.impl.MLTaskApproachInitializer
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun interface MLApiStartupListener {
  fun onBeforeStarted(apiPlatform: MLApiPlatform): MLApiStartupProcessListener
}

@ApiStatus.Internal
data class InitializerAndApproach<T : Any>(
  val initializer: MLTaskApproachInitializer<T>,
  val approach: MLTaskApproach<T>
)

@ApiStatus.Internal
interface MLApiStartupProcessListener {
  fun onStartedInitializingApproaches() {}

  fun onStartedInitializingFus(initializedApproaches: Collection<InitializerAndApproach<*>>) {}

  fun onFinished() {}

  fun onFailed(exception: Throwable?) {}
}
