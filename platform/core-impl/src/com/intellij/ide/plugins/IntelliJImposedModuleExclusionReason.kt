// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface IntelliJImposedModuleExclusionReason : ProductRulesImposedExclusion.ProductRulesImposedExclusionReason

@ApiStatus.Internal
object PluginHasExpiredLicense : IntelliJImposedModuleExclusionReason {
  override fun getLogMessage(): String = "plugin has expired license"
}

@ApiStatus.Internal
object ThirdPartyPrivacyNoticeIsNotAccepted : IntelliJImposedModuleExclusionReason {
  override fun getLogMessage(): String = "third-party privacy notice is not accepted"
}

@ApiStatus.Internal
object NonBundledPluginsLoadingIsDisabled : IntelliJImposedModuleExclusionReason {
  override fun getLogMessage(): String = "non-bundled plugins loading is disabled"
}

@ApiStatus.Internal
object PluginIsNotContainedInTheExplicitlyConfiguredSubsetOfPluginsForLoading : IntelliJImposedModuleExclusionReason {
  override fun getLogMessage(): String = "plugin is not contained in the explicitly configured subset of plugins for loading"
}

@ApiStatus.Internal
object PluginLoadingIsDisabledCompletelyExceptCore : IntelliJImposedModuleExclusionReason {
  override fun getLogMessage(): String = "plugin loading is disabled completely except core"
}

@ApiStatus.Internal
object LegacyPluginIsCompatibleOnlyWithIntelliJIDEA : IntelliJImposedModuleExclusionReason {
  override fun getLogMessage(): String = "legacy plugin is compatible only with IntelliJ IDEA"
}