// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.wm.impl.FrameInfo
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Property
import java.util.concurrent.atomic.AtomicLong

class RecentProjectMetaInfo : BaseState() {
  @get:Attribute
  var opened by property(false)

  @get:Attribute
  var displayName by string()

  // to set frame title as earlier as possible
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

  @Deprecated("")
  @get:OptionTag
  val openPaths by list<String>()

  @get:OptionTag
  val groups by list<ProjectGroup>()
  var pid by string()

  @get:OptionTag
  @get:MapAnnotation(sortBeforeSave = false)
  val additionalInfo by linkedMap<String, RecentProjectMetaInfo>()

  var lastProjectLocation by string()

  fun validateRecentProjects(modCounter: AtomicLong) {
    val limit = AdvancedSettings.getInt("ide.max.recent.projects")
    if (additionalInfo.size <= limit) {
      return
    }

    while (additionalInfo.size > limit) {
      val iterator = additionalInfo.keys.iterator()
      while (iterator.hasNext()) {
        val path = iterator.next()
        if (!additionalInfo[path]!!.opened) {
          iterator.remove()
          break
        }
      }
    }
    modCounter.incrementAndGet()
  }
}