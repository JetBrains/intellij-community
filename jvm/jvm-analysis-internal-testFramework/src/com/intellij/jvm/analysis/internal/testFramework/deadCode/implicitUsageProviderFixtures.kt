// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ImplicitUsageProviderFixtures")

package com.intellij.jvm.analysis.internal.testFramework.deadCode

import com.intellij.jvm.analysis.internal.testFramework.test.addAssertJLibrary
import com.intellij.jvm.analysis.internal.testFramework.test.addEasyMockLibrary
import com.intellij.jvm.analysis.internal.testFramework.test.addJUnit4Library
import com.intellij.jvm.analysis.internal.testFramework.test.addJUnit5Library
import com.intellij.jvm.analysis.internal.testFramework.test.addMockitoLibrary
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.ProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.jetbrains.annotations.TestOnly

/**
 * Creates the project of an AssertJ implicit usage test class. It has the JUnit 5 API and AssertJ, as [AssertJImplicitUsageProviderTestBase] has.
 * Use [com.intellij.jvm.analysis.testFramework.jvmImplicitUsageProviderFixture] for the tests.
 */
@TestOnly
fun assertJImplicitUsageProjectFixture(): TestFixture<LightJavaProject> = lightJavaProjectFixture(LibrariesDescriptor {
  addJUnit5Library()
  addAssertJLibrary()
})

/**
 * Creates the project of an EasyMock implicit usage test class. It has JUnit 4 and EasyMock, as [EasyMockImplicitUsageProviderTestBase] has.
 * Use [com.intellij.jvm.analysis.testFramework.jvmImplicitUsageProviderFixture] for the tests.
 */
@TestOnly
fun easyMockImplicitUsageProjectFixture(): TestFixture<LightJavaProject> = lightJavaProjectFixture(LibrariesDescriptor {
  addJUnit4Library()
  addEasyMockLibrary()
})

/**
 * Creates the project of a Mockito implicit usage test class. It has JUnit 4 and Mockito, as [MockitoImplicitUsageProviderTestBase] has.
 * Use [com.intellij.jvm.analysis.testFramework.jvmImplicitUsageProviderFixture] for the tests.
 */
@TestOnly
fun mockitoImplicitUsageProjectFixture(): TestFixture<LightJavaProject> = lightJavaProjectFixture(LibrariesDescriptor {
  addJUnit4Library()
  addMockitoLibrary()
})

@Suppress("LightProjectDescriptorEqualsHashCode")
private class LibrariesDescriptor(private val addLibraries: ModifiableRootModel.() -> Unit) : ProjectDescriptor(LanguageLevel.HIGHEST) {
  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    super.configureModule(module, model, contentEntry)
    model.addLibraries()
  }
}
