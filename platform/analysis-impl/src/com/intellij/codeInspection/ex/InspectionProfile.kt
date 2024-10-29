// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.codeInspection.InspectionProfile
import com.intellij.configurationStore.SerializableScheme
import com.intellij.openapi.project.Project
import com.intellij.profile.ProfileEx
import com.intellij.profile.codeInspection.BaseInspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProfileManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
@Deprecated("Use 'InspectionProfile.DEFAULT_PROFILE_NAME'", replaceWith = ReplaceWith("InspectionProfile.DEFAULT_PROFILE_NAME"))
const val DEFAULT_PROFILE_NAME: String = InspectionProfile.DEFAULT_PROFILE_NAME

@Deprecated("Use 'InspectionProfileImpl.BASE_PROFILE'", replaceWith = ReplaceWith("InspectionProfileImpl.BASE_PROFILE.get()"))
val BASE_PROFILE: InspectionProfileImpl by lazy { InspectionProfileImpl.BASE_PROFILE.get() }

@Deprecated("Pointless intermediate class; use 'InspectionProfileImpl' directly", replaceWith = ReplaceWith("InspectionProfileImpl"))
abstract class NewInspectionProfile(name: String) : ProfileEx(name), InspectionProfile, SerializableScheme {
  abstract override fun getDisplayName(): String

  private companion object {
    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    @JvmStatic
    @Suppress("FunctionName", "DEPRECATION", "unused", "UNUSED_PARAMETER")
    fun `setToolEnabled$default`(self: NewInspectionProfile, toolShortName: String, enabled: Boolean, project: Project?, fireEvents: Boolean, x1: Int, x2: Any?) {
      (self as InspectionProfileImpl).setToolEnabled(toolShortName, enabled, project, fireEvents)
    }
  }
}

fun createSimple(name: String, project: Project, toolWrappers: List<InspectionToolWrapper<*, *>>): InspectionProfileImpl {
  val profile = InspectionProfileImpl(name, InspectionToolsSupplier.Simple(toolWrappers), InspectionProfileManager.getInstance() as BaseInspectionProfileManager)
  for (toolWrapper in toolWrappers) {
    profile.enableTool(toolWrapper.shortName, project)
  }
  return profile
}
