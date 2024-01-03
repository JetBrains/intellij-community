// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.function.Consumer

@Service
private class MyService(val scope: CoroutineScope)

internal sealed interface SlowFactoryContributors {
  fun setParentDisposable(parentDisposable: Disposable)

  fun onContributorReady(contributorConsumer: Consumer<SearchEverywhereContributor<*>>)

  private object Empty : SlowFactoryContributors {
    override fun setParentDisposable(parentDisposable: Disposable) {
    }

    override fun onContributorReady(contributorConsumer: Consumer<SearchEverywhereContributor<*>>) {
    }
  }

  private class ContributorsInBackground(
    private val job: Job,
    private val contributors: Flow<SearchEverywhereContributor<*>?>
  ) : SlowFactoryContributors, Disposable {
    override fun setParentDisposable(parentDisposable: Disposable) {
      Disposer.register(parentDisposable, this)
    }

    override fun onContributorReady(contributorConsumer: Consumer<SearchEverywhereContributor<*>>) {
      val collectingJob = service<MyService>().scope.launch(CoroutineName("Collecting contributors")) {
        contributors
          .takeWhile { it != null }
          .onEach {
            if (it != null) {
              withContext(Dispatchers.EDT) {
                contributorConsumer.accept(it)
              }
            }
          }
          .collect()
      }

      Disposer.register(this) {
        collectingJob.cancel()
      }
    }

    override fun dispose() {
      job.cancel()
    }
  }

  companion object {
    @JvmStatic
    fun createInBackground(initEvent: AnActionEvent, slowFactories: List<SearchEverywhereContributorFactory<*>>): SlowFactoryContributors {
      if (slowFactories.isEmpty()) {
        return Empty
      }

      val sharedMutableFlow = MutableSharedFlow<SearchEverywhereContributor<*>?>(replay = 100)
      val job = service<MyService>().scope.launch {
        coroutineScope {
          for (factory in slowFactories) {
            launch {
              withContext(CoroutineName("Creating SE contributor for ${factory.javaClass.name}")) {
                val contributor = blockingContext {
                  if (!factory.isAvailable(initEvent.project)) null else factory.createContributor(initEvent)
                }
                if (contributor != null) {
                  if (!contributor.isShownInSeparateTab) {
                    logger<SlowFactoryContributors>().error(
                      "Slow factories allowed to produce only separate tab contributors for now.\n" +
                      "Factory class: ${factory.javaClass.name}, contributor: ${contributor.javaClass.name}"
                    )
                  }
                  sharedMutableFlow.emit(contributor)
                }
              }
            }
          }
        }
        sharedMutableFlow.emit(null)
      }

      return ContributorsInBackground(job, sharedMutableFlow)
    }
  }
}