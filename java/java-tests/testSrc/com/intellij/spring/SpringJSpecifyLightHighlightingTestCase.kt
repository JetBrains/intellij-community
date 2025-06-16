// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spring

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.MavenDependencyUtil

abstract class SpringJSpecifyLightHighlightingTestCase : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = object : ProjectDescriptor(LanguageLevel.HIGHEST) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      MavenDependencyUtil.addFromMaven(model, "org.springframework:spring-core:6.2.3")
      MavenDependencyUtil.addFromMaven(model, "org.jspecify:jspecify:1.0.0")
    }
  }
}