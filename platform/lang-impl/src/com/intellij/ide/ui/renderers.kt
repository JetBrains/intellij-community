// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui

import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.project.Project
import com.intellij.ui.list.SearchAwareRenderer

fun <T> createTargetMainRenderer(project: Project, presentation: (T) -> TargetPresentation?): SearchAwareRenderer<T> {
  return object : TargetPresentationMainRenderer<T>(project) {
    override fun getPresentation(value: T): TargetPresentation? = presentation(value)
  }
}

fun <T> createTargetRenderer(project: Project, presentation: (T) -> TargetPresentation?): SearchAwareRenderer<T> {
  return object : TargetPresentationRenderer<T>(project) {
    override fun getPresentation(value: T): TargetPresentation? = presentation(value)
  }
}
