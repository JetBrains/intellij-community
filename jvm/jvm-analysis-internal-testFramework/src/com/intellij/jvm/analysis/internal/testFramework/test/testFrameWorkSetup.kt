package com.intellij.jvm.analysis.internal.testFramework.test

import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import com.intellij.util.PathUtil
import junit.framework.TestCase
import java.io.File

internal fun ModifiableRootModel.addTestNGLibrary(version: String = "7.10.2") {
  MavenDependencyUtil.addFromMaven(this, "org.testng:testng:$version")
}

internal fun ModifiableRootModel.addJUnit3Library() {
  val jar = File(PathUtil.getJarPathForClass(TestCase::class.java))
  PsiTestUtil.addLibrary(this, "junit3", jar.parent, jar.name)
}

internal fun ModifiableRootModel.addJUnit4Library() {
  val jar = File(PathUtil.getJarPathForClass(org.junit.Test::class.java))
  PsiTestUtil.addLibrary(this, "junit4", jar.parent, jar.name)
}

internal fun ModifiableRootModel.addHamcrestLibrary() {
  val jar = File(PathUtil.getJarPathForClass(org.hamcrest.MatcherAssert::class.java))
  PsiTestUtil.addLibrary(this, "hamcrest-core", jar.parent, jar.name)
  val libraryJar = File(IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("hamcrest").first())
  PsiTestUtil.addLibrary(this, "hamcrest-library", libraryJar.parent, libraryJar.name)
}

internal fun ModifiableRootModel.addJUnit5Library(version: String = "5.9.1") {
  MavenDependencyUtil.addFromMaven(this, "org.junit.jupiter:junit-jupiter-api:$version")
  MavenDependencyUtil.addFromMaven(this, "org.junit.jupiter:junit-jupiter-params:$version")
}

internal fun ModifiableRootModel.addAssertJLibrary(version: String = "3.24.2") {
  MavenDependencyUtil.addFromMaven(this, "org.assertj:assertj-core:$version")
}

internal fun ModifiableRootModel.addMockitoLibrary(version: String = "1.10.19") {
  MavenDependencyUtil.addFromMaven(this, "org.mockito:mockito-all:$version")
}

internal fun ModifiableRootModel.addEasyMockLibrary(version: String = "5.2.0") {
  MavenDependencyUtil.addFromMaven(this, "org.easymock:easymock:$version")
}

internal fun ModifiableRootModel.addMockKLibrary(version: String = "1.13.10") {
  MavenDependencyUtil.addFromMaven(this, "io.mockk:mockk-jvm:$version")
}