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
import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.project.Project
import com.intellij.profile.Profile
import com.intellij.profile.ProfileChangeAdapter
import com.intellij.profile.ProfileEx
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.messages.MessageBus

@JvmField
internal val LOG = Logger.getInstance(BaseInspectionProfileManager::class.java)

abstract class BaseInspectionProfileManager(messageBus: MessageBus) :  InspectionProfileManager {
  protected abstract val schemeManager: SchemeManager<InspectionProfile>

  protected val profileListeners = ContainerUtil.createLockFreeCopyOnWriteList<ProfileChangeAdapter>()
  private val severityRegistrar = SeverityRegistrar(messageBus)

  override final fun getSeverityRegistrar() = severityRegistrar

  override final fun getOwnSeverityRegistrar() = severityRegistrar

  override final fun addProfileChangeListener(listener: ProfileChangeAdapter, parentDisposable: Disposable) {
    ContainerUtil.add(listener, profileListeners, parentDisposable)
  }

  @Suppress("OverridingDeprecatedMember")
  override final fun addProfileChangeListener(listener: ProfileChangeAdapter) {
    profileListeners.add(listener)
  }

  @Suppress("OverridingDeprecatedMember")
  override final fun removeProfileChangeListener(listener: ProfileChangeAdapter) {
    profileListeners.remove(listener)
  }

  internal fun cleanupSchemes(project: Project) {
    for (profile in schemeManager.allSchemes) {
      if ((profile as InspectionProfileImpl).wasInitialized()) {
        profile.cleanup(project)
      }
    }
  }

  override final fun fireProfileChanged(profile: Profile) {
    if (profile is ProfileEx) {
      profile.profileChanged()
    }
    for (adapter in profileListeners) {
      adapter.profileChanged(profile)
    }
  }

  override final fun fireProfileChanged(oldProfile: Profile?, profile: Profile) {
    for (adapter in profileListeners) {
      adapter.profileActivated(oldProfile, profile)
    }
  }

  final fun addProfile(profile: Profile) {
    schemeManager.addScheme(profile as InspectionProfile)
  }

  override final fun deleteProfile(name: String) {
    schemeManager.findSchemeByName(name)?.let {
      schemeManager.removeScheme(it)
      schemeRemoved(it)
    }
  }

  open protected fun schemeRemoved(scheme: InspectionProfile) {
  }

  override fun updateProfile(profile: Profile) {
    schemeManager.addScheme(profile as InspectionProfile)
    fireProfileChanged(profile)
  }
}