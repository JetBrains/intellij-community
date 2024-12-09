// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.tools.logs

import com.intellij.lang.Language
import com.intellij.openapi.util.Version
import com.jetbrains.ml.logs.schema.CustomRuleEventField
import org.jetbrains.annotations.ApiStatus
import com.intellij.internal.statistic.eventLog.events.EventField as IJEventField

@ApiStatus.Internal
sealed class IJSpecificEventField<T>(name: String, lazyDescription: () -> String) : CustomRuleEventField<T>(name, lazyDescription)

@ApiStatus.Internal
class VersionEventField(name: String, lazyDescription: () -> String) : IJSpecificEventField<Version>(name, lazyDescription)

@ApiStatus.Internal
class LanguageEventField(name: String, lazyDescription: () -> String) : IJSpecificEventField<Language>(name, lazyDescription)

@ApiStatus.Internal
open class IJCustomEventField<T>(val baseIJEventField: IJEventField<T>) : IJSpecificEventField<T>(baseIJEventField.name, { baseIJEventField.description ?: "" })
