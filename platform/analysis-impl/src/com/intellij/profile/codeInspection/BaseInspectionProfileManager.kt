/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.profile.codeInspection

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.configurationStore.LazySchemeProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeState
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBus

@JvmField
internal val LOG = Logger.getInstance(BaseInspectionProfileManager::class.java)

abstract class BaseInspectionProfileManager(messageBus: MessageBus) :  InspectionProjectProfileManager() {
  protected abstract val schemeManager: SchemeManager<InspectionProfileImpl>

  private val severityRegistrar = SeverityRegistrar(messageBus)

  override final fun getSeverityRegistrar() = severityRegistrar

  override final fun getOwnSeverityRegistrar() = severityRegistrar

  internal fun cleanupSchemes(project: Project) {
    for (profile in schemeManager.allSchemes) {
      profile.cleanup(project)
    }
  }

  fun addProfile(profile: InspectionProfileImpl) {
    schemeManager.addScheme(profile)
  }

  fun deleteProfile(name: String) {
    schemeManager.removeScheme(name)?.let {
      schemeRemoved(it)
    }
  }

  fun deleteProfile(profile: InspectionProfileImpl) {
    if (schemeManager.removeScheme(profile)) {
      schemeRemoved(profile)
    }
  }

  open protected fun schemeRemoved(scheme: InspectionProfileImpl) {
  }

  abstract fun fireProfileChanged(profile: InspectionProfileImpl)
}

abstract class InspectionProfileProcessor : LazySchemeProcessor<InspectionProfileImpl, InspectionProfileImpl>() {
  override fun getState(scheme: InspectionProfileImpl): SchemeState {
    return if (scheme.wasInitialized()) SchemeState.POSSIBLY_CHANGED else SchemeState.UNCHANGED
  }
}