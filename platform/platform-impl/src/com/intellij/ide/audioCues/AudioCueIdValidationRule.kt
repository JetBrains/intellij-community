// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.jetbrains.fus.reporting.api.IEventContext
import com.jetbrains.fus.reporting.api.ValidationResultType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class AudioCueIdValidationRule : CustomValidationRule() {
  override fun getRuleId(): String = "audio_cue_id"

  override fun doValidate(data: String, context: IEventContext): ValidationResultType {
    val plugin = AudioCueProvider.EP_NAME.filterableLazySequence()
                   .firstOrNull { extension -> extension.instance?.audioCues?.any { it.id == data } == true }
                   ?.pluginDescriptor
                 ?: return ValidationResultType.REJECTED
    return if (getPluginInfoByDescriptor(plugin).isSafeToReport()) ValidationResultType.ACCEPTED
    else ValidationResultType.THIRD_PARTY
  }
}
