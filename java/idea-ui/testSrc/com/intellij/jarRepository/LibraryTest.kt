// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.ui.UIUtil
import org.jetbrains.concurrency.Promise
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

abstract class LibraryTest : UsefulTestCase() {
  protected lateinit var myProject: Project
  protected lateinit var myMavenLocalCache: File
  protected lateinit var myMavenRepo: File
  protected lateinit var myMavenRepoDescription: RemoteRepositoryDescription

  private lateinit var myFixture: IdeaProjectTestFixture

  override fun setUp() {
    super.setUp()
    myFixture = runInEdtAndGet {
      IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder(getTestName(false)).fixture.apply { setUp() }
    }
    myProject = myFixture.project
    myMavenRepo = FileUtil.createTempDirectory("maven", "repo")
    myMavenLocalCache = FileUtil.createTempDirectory("maven", "cache")
    myMavenRepoDescription = RemoteRepositoryDescription("id", "name", myMavenRepo.toURI().toURL().toString())
    JarRepositoryManager.setLocalRepositoryPath(myMavenLocalCache)
  }

  override fun tearDown() {
    RunAll(
      { myFixture.tearDown() },
      { super.tearDown() },
    ).run()
  }

  protected fun createLibrary(): LibraryEx {
    val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
    val library = runWriteActionAndWait { libraryTable.createLibrary("NewLibrary") }
    disposeOnTearDown(object : Disposable {
      override fun dispose() {
        runWriteActionAndWait { libraryTable.removeLibrary(library) }
      }
    })
    return library as LibraryEx
  }

  protected fun <T> getResult(promise: Promise<T>): T? {
    var result: T? = null
    (1..5).forEach {
      try {
        UIUtil.dispatchAllInvocationEvents()
        result = promise.blockingGet(1, TimeUnit.SECONDS)
        return@forEach
      }
      catch (e: TimeoutException) {
      }
    }
    return result
  }
}