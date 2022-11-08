// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection

import com.intellij.openapi.project.Project

interface ProjectBasedInspectionProfileManager {
  val project: Project
}