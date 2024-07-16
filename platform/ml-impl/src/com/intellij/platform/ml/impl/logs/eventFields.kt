// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.logs

import com.intellij.lang.Language
import com.intellij.openapi.util.Version
import com.intellij.platform.ml.logs.schema.CustomRuleEventField
import org.jetbrains.annotations.ApiStatus
import com.intellij.internal.statistic.eventLog.events.EventField as IJEventField

@ApiStatus.Internal
sealed class IJSpecificEventField<T>(name: String, description: String?) : CustomRuleEventField<T>(name, description)

@ApiStatus.Internal
class VersionEventField(name: String, description: String?) : IJSpecificEventField<Version>(name, description)

@ApiStatus.Internal
class LanguageEventField(name: String, description: String?) : IJSpecificEventField<Language>(name, description)

@ApiStatus.Internal
open class CustomEventField<T>(val baseIJEventField: IJEventField<T>) : IJSpecificEventField<T>(baseIJEventField.name, baseIJEventField.description)
