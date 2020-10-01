// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.util.ProgressIndicatorListenerAdapter
import com.intellij.openapi.progress.util.RelayUiToDelegateIndicator
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.UnknownMissingSdk
import com.intellij.openapi.projectRoots.impl.UnknownSdkFixAction
import com.intellij.openapi.roots.ui.configuration.UnknownSdkResolver.UnknownSdkLookup
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.util.Consumer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Predicate
import java.util.function.Supplier

private open class SdkLookupContext(private val params: SdkLookupParameters) {
  private val sdkNameCallbackExecuted = AtomicBoolean(false)
  private val sdkCallbackExecuted = AtomicBoolean(false)

  val sdkName= params.sdkName
  val sdkType = params.sdkType
  val testSdkSequence = params.testSdkSequence
  val project = params.project
  val progressMessageTitle = params.progressMessageTitle

  val sdkHomeFilter= params.sdkHomeFilter
  val versionFilter= params.versionFilter
  val onBeforeSdkSuggestionStarted = params.onBeforeSdkSuggestionStarted
  val onLocalSdkSuggested = params.onLocalSdkSuggested
  val onDownloadableSdkSuggested = params.onDownloadableSdkSuggested

  val onSdkNameResolvedConsumer = Consumer<Sdk?> { onSdkNameResolved(it) }
  val onSdkResolvedConsumer = Consumer<Sdk?> { onSdkResolved(it) }

  fun attachIndicatorIfNeeded(rootProgressIndicator: ProgressIndicatorBase) {
    val indicator = params.progressIndicator ?: return
    if (indicator is ProgressIndicatorEx) {
      rootProgressIndicator.addStateDelegate(indicator)
    } else {
      rootProgressIndicator.addStateDelegate(RelayUiToDelegateIndicator(indicator))
    }
  }

  fun onSdkNameResolved(sdk: Sdk?) {
    if (!sdkNameCallbackExecuted.compareAndSet(false, true)) return
    params.onSdkNameResolved.invoke(sdk)
  }

  fun onSdkResolved(sdk: Sdk?) {
    onSdkNameResolved(sdk)

    if (!sdkCallbackExecuted.compareAndSet(false, true)) return

    if (sdk != null && !checkSdkHomeAndVersion(sdk)) {
      params.onSdkResolved.invoke(null)
    } else {
      params.onSdkResolved.invoke(sdk)
    }
  }

  fun checkSdkHomeAndVersion(sdk: Sdk?): Boolean {
    val sdkHome = sdk?.homePath ?: return false
    return params.sdkHomeFilter?.invoke(sdkHome) != false && checkSdkVersion(sdk)
  }

  fun checkSdkVersion(sdk: Sdk?) : Boolean {
    val versionString = sdk?.versionString ?: return false
    return params.versionFilter?.invoke(versionString) != false
  }

  val listener = object: UnknownSdkFixAction.Listener {
    override fun onSdkNameResolved(sdk: Sdk) {
      this@SdkLookupContext.onSdkNameResolved(sdk)
    }

    override fun onSdkReady(sdk: Sdk) {
      this@SdkLookupContext.onSdkNameResolved(sdk)
    }

    override fun onResolveFailed() {
      this@SdkLookupContext.onSdkNameResolved(null)
    }
  }

  override fun toString(): String = "SdkLookupContext($params)"
}

private val LOG = logger<SdkLookupImpl>()

internal class SdkLookupImpl : SdkLookup {
  override fun createBuilder(): SdkLookupBuilder = CommonSdkLookupBuilder { service<SdkLookup>().lookup(it) }

  override fun lookup(lookup: SdkLookupParameters) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    SdkLookupContextEx(lookup).lookup()
  }

  override fun lookupBlocking(lookup: SdkLookupParameters) {
    object : SdkLookupContextEx(lookup) {
      override fun awaitPendingDownload(downloadLatch: CountDownLatch, rootProgressIndicator: ProgressIndicatorBase) {
        //busy waiting for SDK download to complete
        try {
          while (true) {
            rootProgressIndicator.checkCanceled()
            if (downloadLatch.await(500, TimeUnit.MILLISECONDS)) break
          }
        } catch (e: InterruptedException) {
          rootProgressIndicator.checkCanceled()
          throw ProcessCanceledException()
        }
      }

      override fun runSdkResolutionUnderProgress(rootProgressIndicator: ProgressIndicatorBase, task: Progressive) {
        //it is already running under progress, no need to open yet another one
        task.run(rootProgressIndicator)
      }
    }.lookup()
  }
}

private open class SdkLookupContextEx(lookup: SdkLookupParameters) : SdkLookupContext(lookup) {
  fun lookup() {
    val rootProgressIndicator = ProgressIndicatorBase()
    attachIndicatorIfNeeded(rootProgressIndicator)

    sequence {
      val namedSdk = runReadAction {
        sdkName?.let {
          when (sdkType) {
            null -> ProjectJdkTable.getInstance().findJdk(sdkName)
            else -> ProjectJdkTable.getInstance().findJdk(sdkName, sdkType.name)
          }
        }
      }

      //include currently downloading Sdks
      yieldAll(SdkDownloadTracker.getInstance().findDownloadingSdks(sdkName))

      yield(namedSdk)

      yieldAll(testSdkSequence)
    } .onEach { rootProgressIndicator.checkCanceled() }
      .filterNotNull()
      .filter { candidate -> sdkType == null || candidate.sdkType == sdkType }
      .filter { checkSdkVersion(it) }
      .forEach {
        if (testSdkAndWaitForDownloadIfNeeded(it, rootProgressIndicator)) return
        if (testExistingSdk(it)) return
      }

    continueSdkLookupWithSuggestions(rootProgressIndicator)
  }

  open fun testSdkAndWaitForDownloadIfNeeded(sdk: Sdk, rootProgressIndicator: ProgressIndicatorBase) : Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()

    val latch = CountDownLatch(1)
    val disposable = Disposer.newDisposable()
    val onDownloadCompleted = Consumer<Boolean> { onSucceeded ->
      Disposer.dispose(disposable)

      val finalSdk = when {
        onSucceeded && checkSdkHomeAndVersion(sdk) -> sdk
        onSucceeded -> {
          //TODO: un such a case it will not attempt to continue resolution
          LOG.warn("Just downloaded SDK: $sdk has failed the checkSdkHomeAndVersion test")
          null
        }
        else -> null
      }

      onSdkResolved(finalSdk)
      latch.countDown()
    }

    val isDownloading = invokeAndWaitIfNeeded {
      SdkDownloadTracker
        .getInstance()
        .tryRegisterDownloadingListener(
          sdk,
          disposable,
          rootProgressIndicator,
          onDownloadCompleted)
    }

    if (!isDownloading) {
      Disposer.dispose(disposable)
      return false
    }

    //it will be notified later when the download is completed
    onSdkNameResolved(sdk)

    //wait for download to complete
    awaitPendingDownload(latch, rootProgressIndicator)
    return true
  }

  open fun awaitPendingDownload(downloadLatch: CountDownLatch, rootProgressIndicator: ProgressIndicatorBase) = Unit

  private fun testExistingSdk(sdk: Sdk): Boolean {
    //it could be the case with an ordinary SDK, it may not pass the test below
    if (checkSdkHomeAndVersion(sdk)) {
      onSdkResolved(sdk)
      return true
    }

    return false
  }

  private fun continueSdkLookupWithSuggestions(rootProgressIndicator: ProgressIndicatorBase) {
    if (sdkType == null) {
      //it is not possible to suggest everything, if [sdkType] is not specified
      onSdkResolved(null)
      return
    }

    if (onBeforeSdkSuggestionStarted() == SdkLookupDecision.STOP) {
      onSdkResolved(null)
      return
    }

    val unknownSdk = object: UnknownSdk {
      val versionPredicate = versionFilter?.let { Predicate<String> { versionFilter.invoke(it) } }

      override fun getSdkName() = this@SdkLookupContextEx.sdkName
      override fun getSdkType() : SdkType = this@SdkLookupContextEx.sdkType
      override fun getSdkVersionStringPredicate() = versionPredicate
      override fun getSdkHomePredicate() = sdkHomeFilter?.let { filter -> Predicate<String> { path -> filter(path) } }
      override fun toString() = "SdkLookup{${sdkType.presentableName}, ${versionPredicate} }"
    }

    runSdkResolutionUnderProgress(rootProgressIndicator) { indicator ->
      try {
        val resolvers = UnknownSdkResolver.EP_NAME.iterable
          .mapNotNull { it.createResolver(project, indicator) }

        indicator.checkCanceled()

        val possibleFix = UnknownMissingSdk.createMissingFixAction(
          unknownSdk,
          Supplier { resolveLocalFix(resolvers, unknownSdk, indicator) },
          Supplier { resolveDownloadFix(resolvers, unknownSdk, indicator) }
        )

        if (possibleFix != null) {
          possibleFix.addSuggestionListener(listener)
          possibleFix.applySuggestionAsync(project)
        }

        onSdkNameResolved(null)
      } catch (e: ProcessCanceledException) {
        onSdkResolved(null)
        throw e
      } catch (t: Throwable) {
        LOG.warn("Failed to resolve SDK for ${this@SdkLookupContextEx}. ${t.message}", t)

        onSdkResolved(null)
      }
    }
  }

  private fun resolveLocalFix(resolvers: List<UnknownSdkLookup>,
                              unknownSdk: UnknownSdk,
                              indicator: ProgressIndicator) = indicator.withPushPop {
    indicator.text = ProjectBundle.message("progress.text.looking.for.local.sdks")
    resolvers
                     .asSequence()
                     .onEach { indicator.checkCanceled() }
                     .mapNotNull { it.proposeLocalFix(unknownSdk, indicator) }
                     .filter { versionFilter?.invoke(it.versionString) != false }
                     .filter { sdkHomeFilter?.invoke(it.existingSdkHome) != false }
                     .onEach { indicator.checkCanceled() }
                     .filter { onLocalSdkSuggested.invoke(it) == SdkLookupDecision.CONTINUE }
                     .firstOrNull()
  }

  private fun resolveDownloadFix(resolvers: List<UnknownSdkLookup>,
                                 unknownSdk: UnknownSdk,
                                 indicator: ProgressIndicator) = indicator.withPushPop {
    indicator.text = ProjectBundle.message("progress.text.looking.for.downloadable.sdks")
    resolvers
                        .asSequence()
                        .onEach { indicator.checkCanceled() }
                        .mapNotNull { it.proposeDownload(unknownSdk, indicator) }
                        .filter { versionFilter?.invoke(it.versionString) != false }
                        .onEach { indicator.checkCanceled() }
                        .filter { onDownloadableSdkSuggested.invoke(it) == SdkLookupDecision.CONTINUE }
                        .firstOrNull()
  }

  private fun runSdkResolutionUnderProgress(rootProgressIndicator: ProgressIndicatorBase,
                                            action: (ProgressIndicator) -> Unit) {
    val task = Progressive { indicator ->
        object : ProgressIndicatorListenerAdapter() {
          override fun cancelled() {
            rootProgressIndicator.cancel()
          }
        }.installToProgressIfPossible(indicator)

        val relayToVisibleIndicator: ProgressIndicatorEx = RelayUiToDelegateIndicator(indicator)
        rootProgressIndicator.addStateDelegate(relayToVisibleIndicator)

        try {
          action(rootProgressIndicator)
        }
        finally {
          rootProgressIndicator.removeStateDelegate(relayToVisibleIndicator)
        }
      }

    runSdkResolutionUnderProgress(rootProgressIndicator, task)
  }

  open fun runSdkResolutionUnderProgress(rootProgressIndicator: ProgressIndicatorBase, task: Progressive) {
    val sdkTypeName = sdkType?.presentableName ?: ProjectBundle.message("sdk")
    val title = progressMessageTitle ?: ProjectBundle.message("sdk.lookup.resolving.sdk.progress", sdkTypeName)
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true, ALWAYS_BACKGROUND), Progressive by task {})
  }
}
