// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.logs

import com.intellij.lang.Language
import com.intellij.openapi.util.Version
import com.intellij.platform.ml.logs.schema.CustomRuleEventField
import org.jetbrains.annotations.ApiStatus
import com.intellij.internal.statistic.eventLog.events.EventField as IJEventField

@ApiStatus.Internal
sealed class IJSpecificEventField<T>(name: String, descriptionProvider: () -> String) : CustomRuleEventField<T>(name, descriptionProvider)

@ApiStatus.Internal
class VersionEventField(name: String, descriptionProvider: () -> String) : IJSpecificEventField<Version>(name, descriptionProvider)

@ApiStatus.Internal
class LanguageEventField(name: String, descriptionProvider: () -> String) : IJSpecificEventField<Language>(name, descriptionProvider)

@ApiStatus.Internal
open class CustomEventField<T>(val baseIJEventField: IJEventField<T>) : IJSpecificEventField<T>(baseIJEventField.name, { baseIJEventField.description ?: "" })
