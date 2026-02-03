// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PushUtils")

package com.intellij.dvcs.push.ui

import com.intellij.dvcs.push.PushTarget

/**
 * Checks if there are [targets][PushTarget] prohibited from force pushing, among push specs currently selected in the [VcsPushUi],
 * and returns one if found, or null if all selected targets are allowed to be force pushed to.
 */
fun getProhibitedTarget(ui: VcsPushUi): PushTarget? {
  for ((support, pushInfo) in ui.selectedPushSpecs) {
    val prohibited = pushInfo.find { !support.isForcePushAllowed(it.repository, it.pushSpec.target) }
    if (prohibited != null) return prohibited.pushSpec.target
  }
  return null
}

fun getProhibitedTargetConfigurablePath(ui: VcsPushUi): String? {
  for ((support, pushInfo) in ui.selectedPushSpecs) {
    val prohibited = pushInfo.find { !support.isForcePushAllowed(it.repository, it.pushSpec.target) }
    if (prohibited != null) return support.forcePushConfigurablePath
  }
  for ((support, _) in ui.selectedPushSpecs) {
    return support.forcePushConfigurablePath
  }
  return null
}

