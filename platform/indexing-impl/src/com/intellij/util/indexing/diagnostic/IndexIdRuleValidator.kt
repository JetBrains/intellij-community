// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.PluginType
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.psi.stubs.StubIndexExtension
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.ID

/**
 * Basically it checks incoming string is contained in {@linkplain ID} internal map, and if it does, then
 * checks associated plugin (which registers that indexId) is by JetBrains. 
 *
 * There are few things on the top of that, extensively documented in kotlin.
 */
class IndexIdRuleValidator : CustomValidationRule() {
  override fun getRuleId(): String = "index_id"

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    if (isThirdPartyValue(data)) {
      return ValidationResultType.ACCEPTED
    }

    val runningFromSources = PluginManagerCore.isRunningFromSources()

    val id = ID.findByName<Any, Any>(data) ?: return ValidationResultType.REJECTED
    val pluginId = id.pluginId
    if (pluginId != null) {
      return validatePlugin(getPluginInfoById(pluginId))
    }else if(!runningFromSources){
      //if pluginId is null & not from sources -> this is core index, so allow it
      return ValidationResultType.ACCEPTED;
    }else{ // if (runningFromSources)

      //RC: isn't it taxing to scan the lists of all file/stub indexes on _each_ indexId validation?
      val extension = FileBasedIndexExtension.EXTENSION_POINT_NAME.findFirstSafe { ex -> ex.name.name == data }
      if (extension != null) {
        return validatePlugin(getPluginInfo(extension::class.java))
      }

      val stubExtension = StubIndexExtension.EP_NAME.findFirstSafe { ex -> ex.key.name == data }
      if (stubExtension != null) {
        return validatePlugin(getPluginInfo(stubExtension::class.java))
      }
    }

    return ValidationResultType.REJECTED
  }

  private fun validatePlugin(info: PluginInfo): ValidationResultType {
    if (info.type === PluginType.UNKNOWN) {
      return ValidationResultType.REJECTED
    }

    return if (info.isDevelopedByJetBrains()) ValidationResultType.ACCEPTED else ValidationResultType.THIRD_PARTY
  }
}