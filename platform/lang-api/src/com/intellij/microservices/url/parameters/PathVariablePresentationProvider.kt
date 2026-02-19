// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.url.parameters

import com.intellij.icons.AllIcons
import com.intellij.ide.presentation.PresentationProvider
import com.intellij.microservices.MicroservicesBundle
import com.intellij.pom.PomTarget
import javax.swing.Icon

internal class PathVariablePresentationProvider : PresentationProvider<PomTarget>() {
  override fun getTypeName(t: PomTarget): String = MicroservicesBundle.message("microservices.url.path.variable.typeName")

  override fun getIcon(t: PomTarget): Icon = AllIcons.Nodes.Variable
}