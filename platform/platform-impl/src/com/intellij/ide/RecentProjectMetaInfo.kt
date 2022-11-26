// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.wm.impl.FrameInfo
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Property
import java.util.concurrent.atomic.LongAdder

class RecentProjectMetaInfo : BaseState() {
  @get:Attribute
  var opened by property(false)

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

  fun validateRecentProjects(modCounter: LongAdder) {
    val limit = AdvancedSettings.getInt("ide.max.recent.projects")
    if (additionalInfo.size <= limit || limit < 1) {
      return
    }

    // might be freezing for many projects that were stored as "opened"
    while (additionalInfo.size > limit) {
      val iterator = additionalInfo.keys.iterator()
      while (iterator.hasNext()) {
        val path = iterator.next()
        if (!additionalInfo.get(path)!!.opened) {
          iterator.remove()
          break
        }
      }
    }
    modCounter.increment()
  }
}