package com.intellij.platform.ml.impl.logs.fus

import com.intellij.lang.Language
import com.intellij.openapi.util.Version
import com.jetbrains.ml.features.api.logs.CustomRuleEventField
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
