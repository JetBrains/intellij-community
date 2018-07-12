// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  final override fun getSeverityRegistrar(): SeverityRegistrar = severityRegistrar

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