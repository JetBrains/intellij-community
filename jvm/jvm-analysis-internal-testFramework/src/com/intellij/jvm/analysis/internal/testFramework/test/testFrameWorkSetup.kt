package com.intellij.jvm.analysis.internal.testFramework.test

import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import com.intellij.util.PathUtil
import junit.framework.TestCase
import java.io.File

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

internal fun ModifiableRootModel.addAssertJLibrary() {
  MavenDependencyUtil.addFromMaven(this, "org.assertj:assertj-core:3.24.2")
}