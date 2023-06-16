// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel
import com.intellij.configurationStore.LazySchemeProcessor
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeState
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBus

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
    if (profile is InspectionProfileModifiableModel) deleteProfile(profile.source.name) else deleteProfile(profile.name)
  }

  protected open fun schemeRemoved(scheme: InspectionProfileImpl) {
    scheme.cleanup(null)
  }

  abstract fun fireProfileChanged(profile: InspectionProfileImpl)
}

abstract class InspectionProfileProcessor : LazySchemeProcessor<InspectionProfileImpl, InspectionProfileImpl>() {
  override fun getState(scheme: InspectionProfileImpl): SchemeState {
    return if (scheme.wasInitialized()) SchemeState.POSSIBLY_CHANGED else SchemeState.UNCHANGED
  }
}