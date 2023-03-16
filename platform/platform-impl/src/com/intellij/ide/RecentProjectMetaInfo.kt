// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.wm.impl.FrameInfo
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Property
import java.awt.Rectangle

class RecentProjectMetaInfo : BaseState() {
  @get:Attribute
  var opened by property(false)

  /**
   * If true, the project will not be reopened on startup and not displayed in the recent projects list.
   * Suitable for internal projects, that should not be accessed by usual ways of opening projects.
   */
  @get:Attribute
  var hidden by property(false)

  @get:Attribute
  var displayName by string()

  // to set frame title as early as possible
  @get:Attribute
  var frameTitle by string()

  var build by string()
  var productionCode by string()
  var eap by property(false)
  var binFolder by string()
  var projectOpenTimestamp by property(0L)
  var buildTimestamp by property(0L)
  var activationTimestamp by property(0L)
  var metadata by string()

  @get:Attribute
  var projectWorkspaceId by string()

  @get:Property(surroundWithTag = false)
  internal var frame: FrameInfo? by property()
  @IntellijInternalApi
  val windowBounds: Rectangle?
    get() = frame?.bounds
}

class RecentProjectManagerState : BaseState() {
  @Deprecated("")
  @get:OptionTag
  val recentPaths by list<String>()

  @get:OptionTag
  val groups by list<ProjectGroup>()
  var pid by string()

  @get:OptionTag
  @get:MapAnnotation(sortBeforeSave = false)
  val additionalInfo by linkedMap<String, RecentProjectMetaInfo>()

  var lastProjectLocation by string()

  var lastOpenedProject by string()
}