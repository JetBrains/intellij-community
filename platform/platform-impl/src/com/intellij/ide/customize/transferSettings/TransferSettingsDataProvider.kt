// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings

import com.intellij.ide.customize.transferSettings.models.BaseIdeVersion
import com.intellij.ide.customize.transferSettings.models.FailedIdeVersion
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.providers.TransferSettingsProvider
import com.intellij.openapi.diagnostic.logger
import java.util.*
import java.util.stream.Collectors

class TransferSettingsDataProvider(private val providers: List<TransferSettingsProvider>) {
  private val baseIdeVersions = mutableListOf<BaseIdeVersion>()
  private val ideVersions = mutableListOf<IdeVersion>()
  private val failedIdeVersions = mutableListOf<FailedIdeVersion>()

  val orderedIdeVersions get() = ideVersions + failedIdeVersions

  constructor(vararg providers: TransferSettingsProvider) : this(providers.toList())

  fun refresh(): TransferSettingsDataProvider {
    val newBase = TransferSettingsDataProviderSession(providers, baseIdeVersions.map { it.id }).baseIdeVersions
    baseIdeVersions.addAll(newBase)

    ideVersions.addAll(newBase.filterIsInstance<IdeVersion>())
    ideVersions.sortByDescending { it.lastUsed }

    failedIdeVersions.addAll(newBase.filterIsInstance<FailedIdeVersion>())

    return this
  }
}

private class TransferSettingsDataProviderSession(private val providers: List<TransferSettingsProvider>,
                                                  private val skipIds: List<String>?) {
  private val logger = logger<TransferSettingsDataProviderSession>()

  val baseIdeVersions: List<BaseIdeVersion> by lazy { createBaseIdeVersions() }

  private fun createBaseIdeVersions() = providers
    .parallelStream()
    .flatMap { provider ->
      if (!provider.isAvailable()) {
        logger.info("Provider ${provider.name} is not available")
        return@flatMap null
      }

      try {
        provider.getIdeVersions(skipIds ?: emptyList()).stream()
      }
      catch (t: Throwable) {
        logger.warn("Failed to get base ide versions", t)
        return@flatMap null
      }
    }
    .filter(Objects::nonNull)
    .collect(Collectors.toList())
}