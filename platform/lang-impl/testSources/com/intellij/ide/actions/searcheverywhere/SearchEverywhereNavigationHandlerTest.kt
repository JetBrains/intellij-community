// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class SearchEverywhereNavigationHandlerTest {
  companion object {
    private val firstProjectFixture = projectFixture()
    private val firstFileFixture = firstProjectFixture.moduleFixture()
      .sourceRootFixture()
      .psiFileFixture("first.txt", "first")

    private val secondProjectFixture = projectFixture()
    private val secondFileFixture = secondProjectFixture.moduleFixture()
      .sourceRootFixture()
      .psiFileFixture("second.txt", "second")
  }

  @Test
  fun `blocked navigation does not block another project`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val blockedNavigation = GatedNavigationService()
    val freeNavigation = GatedNavigationService().apply { openGate() }
    firstProjectFixture.get().replaceService(NavigationService::class.java, blockedNavigation, disposable)
    secondProjectFixture.get().replaceService(NavigationService::class.java, freeNavigation, disposable)

    navigateTo(firstProjectFixture.get(), readAction { firstFileFixture.get() }, "first")
    blockedNavigation.entered.await()

    try {
      navigateTo(secondProjectFixture.get(), readAction { secondFileFixture.get() }, "second")
      withTimeout(navigationTimeout) { freeNavigation.completed.await() }
    }
    finally {
      blockedNavigation.openGate()
      blockedNavigation.completed.await()
    }
  }

  private fun navigateTo(project: Project, file: PsiFile, searchText: String) {
    SearchEverywhereNavigationHandler(project).gotoSelectedItem(file, 0, searchText)
  }
}

private val navigationTimeout = 5.seconds

/**
 * Holds each navigation at [gate] until [openGate] is called.
 */
private class GatedNavigationService : NavigationService {
  private val gate = CompletableDeferred<Unit>()
  val entered = CompletableDeferred<Unit>()
  val completed = CompletableDeferred<Unit>()

  fun openGate() {
    gate.complete(Unit)
  }

  override suspend fun navigateRequests(
    options: NavigationOptions,
    supplier: suspend () -> Collection<NavigationRequest>,
  ): Boolean {
    entered.complete(Unit)
    gate.await()
    completed.complete(Unit)
    return true
  }

  override suspend fun navigate(request: NavigationRequest, options: NavigationOptions): Boolean =
    error("Unexpected navigation")

  override suspend fun navigate(requests: Collection<NavigationRequest>, options: NavigationOptions): Boolean =
    error("Unexpected navigation")

  override suspend fun navigate(navigatables: List<Navigatable>, options: NavigationOptions): Boolean =
    error("Unexpected navigation")
}
