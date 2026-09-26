// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import com.intellij.java.JavaBundle

public class ExternalJavaConfigurationGroupSettingProvider : CodeVisionGroupSettingProvider {
  public companion object {
    public const val GROUP_ID: String = "java.configuration"
  }

  override val groupId: String
    get() = GROUP_ID

  override val groupName: String
    get() = JavaBundle.message("code.vision.java.external.configuration.name")

  override val description: String
    get() = JavaBundle.message("code.vision.java.external.configuration.description")
}