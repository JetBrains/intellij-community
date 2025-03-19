// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.fus

import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.lang.Language
import com.intellij.openapi.util.Version
import com.jetbrains.ml.api.logs.RuleBasedStringEventField
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
class VersionEventField(name: String, lazyDescription: () -> String) : RuleBasedStringEventField<Version>(name, EventFields.Version.validationRule.first(), lazyDescription, { it.toCompactString() })

@ApiStatus.Internal
class LanguageEventField(name: String, lazyDescription: () -> String) : RuleBasedStringEventField<Language>(name, EventFields.Language.validationRule.first(), lazyDescription, { it.id })
