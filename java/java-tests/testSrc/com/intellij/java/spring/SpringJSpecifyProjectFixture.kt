// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.spring

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture

private val SPRING_JSPECIFY_DESCRIPTOR = object : LightJavaCodeInsightFixtureTestCase.ProjectDescriptor(LanguageLevel.HIGHEST) {
  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    super.configureModule(module, model, contentEntry)
    MavenDependencyUtil.addFromMaven(model, "org.springframework:spring-core:6.2.3")
    MavenDependencyUtil.addFromMaven(model, "org.jspecify:jspecify:1.0.0")
  }
}

/**
 * Creates the project of the Spring contract tests: the highest language level, `spring-core` 6.2.3 and `jspecify` 1.0.0.
 * Declare it in a companion object.
 */
internal fun springJSpecifyProjectFixture(): TestFixture<LightJavaProject> = lightJavaProjectFixture(SPRING_JSPECIFY_DESCRIPTOR)
