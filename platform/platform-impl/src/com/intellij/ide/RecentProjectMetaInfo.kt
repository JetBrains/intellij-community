// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.impl.FrameInfo
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Property
import java.awt.Rectangle

class RecentProjectMetaInfo : BaseState() {
  @get:Attribute
  var opened: Boolean by property(false)

  /**
   * If true, the project will not be reopened on startup and not displayed in the recent projects list.
   * Suitable for internal projects, that should not be accessed by usual ways of opening projects.
   */
  @get:Attribute
  var hidden: Boolean by property(false)

  @get:Attribute
  var displayName: @NlsSafe String? by string()

  // to set frame title as early as possible
  @get:Attribute
  var frameTitle: String? by string()

  var build: String? by string()
  var productionCode: String? by string()
  var eap: Boolean by property(false)
  var binFolder: String? by string()
  var projectOpenTimestamp: Long by property(0L)
  var buildTimestamp: Long by property(0L)
  var activationTimestamp: Long by property(0L)
  var metadata: String? by string()
  var colorInfo: RecentProjectColorInfo by property(RecentProjectColorInfo())

  @get:Attribute
  var projectWorkspaceId: String? by string()

  @get:Property(surroundWithTag = false)
  internal var frame: FrameInfo? by property()
  @IntellijInternalApi
  val windowBounds: Rectangle?
    get() = frame?.bounds
}

class RecentProjectManagerState : BaseState() {
  @Deprecated("")
  @get:OptionTag
  val recentPaths: MutableList<String> by list()

  @get:OptionTag
  val groups: MutableList<ProjectGroup> by list()
  var pid: String? by string()

  @get:OptionTag
  @get:MapAnnotation(sortBeforeSave = false)
  val additionalInfo: MutableMap<String, RecentProjectMetaInfo> by linkedMap()

  var lastProjectLocation: String? by string()

  var lastOpenedProject: String? by string()

  var forceReopenProjects: Boolean by property(false)
}