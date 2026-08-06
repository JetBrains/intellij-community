// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface IntelliJImposedModuleExclusionReason : ProductRulesImposedExclusion.ProductRulesImposedExclusionReason

@ApiStatus.Internal
class PluginHasExpiredLicense : IntelliJImposedModuleExclusionReason

@ApiStatus.Internal
class ThirdPartyPrivacyNoticeIsNotAccepted : IntelliJImposedModuleExclusionReason
