/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection.ex

import com.intellij.codeInspection.InspectionProfile
import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import gnu.trove.THashMap

internal class VisibleTreeStateComponent : BaseState() {
  @get:Property(surroundWithTag = false)
  @get:MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
  private var profileNameToState by bean<MutableMap<String, VisibleTreeState>>(THashMap())

  fun copyFrom(state: VisibleTreeStateComponent) {
    copyFrom(state)
    profileNameToState.clear()
    profileNameToState.putAll(state.profileNameToState)
  }

  fun getVisibleTreeState(profile: InspectionProfile) = profileNameToState.getOrPut(profile.name) { VisibleTreeState() }
}
