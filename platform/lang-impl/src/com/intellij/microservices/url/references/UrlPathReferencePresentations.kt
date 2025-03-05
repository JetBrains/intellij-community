// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.url.references

import com.intellij.icons.AllIcons
import com.intellij.ide.presentation.PresentationProvider
import com.intellij.microservices.MicroservicesBundle
import com.intellij.pom.PomTarget
import javax.swing.Icon

internal class UrlPathSegmentPresentationProvider : PresentationProvider<PomTarget>() {
  override fun getTypeName(t: PomTarget): String = MicroservicesBundle.message("microservices.url.path.segment")

  override fun getIcon(t: PomTarget): Icon = AllIcons.Javaee.WebService
}

internal class AuthorityPresentationProvider : PresentationProvider<PomTarget>() {
  override fun getTypeName(t: PomTarget): String = MicroservicesBundle.message("microservices.url.path.authority")

  override fun getIcon(t: PomTarget): Icon = AllIcons.Javaee.WebService
}

internal class QueryParameterPresentationProvider : PresentationProvider<PomTarget>() {
  override fun getTypeName(t: PomTarget): String = MicroservicesBundle.message("microservices.url.query.parameter")

  override fun getIcon(t: PomTarget): Icon = AllIcons.Nodes.Parameter
}