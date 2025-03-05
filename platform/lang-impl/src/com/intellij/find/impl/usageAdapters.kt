// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageInfoAdapter
import com.intellij.usages.impl.UsageViewCoroutineScopeProvider
import com.intellij.util.io.await
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

@ApiStatus.Internal
fun getUsageInfoAsFuture(adapters: List<UsageInfoAdapter>, project: Project): CompletableFuture<List<UsageInfo>> {
  return UsageViewCoroutineScopeProvider.getInstance(project).coroutineScope.async {
    val futures: Array<CompletableFuture<Array<UsageInfo>>> = readAction {
      adapters.filter { it.isValid }.map { it.mergedInfosAsync }.toTypedArray()
    }
    CompletableFuture.allOf(*futures).await()
    futures.map { it.get() }.flatMap { x -> x.toList() }
  }.asCompletableFuture()
}

@ApiStatus.Internal
suspend fun getUsageInfo(adapters: List<UsageInfoAdapter>): List<UsageInfo> {
  return readAction {
    adapters.filter { it.isValid }.map { it.mergedInfos }.toTypedArray().flatMap { x -> x.toList() }
  }
}
