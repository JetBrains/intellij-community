// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.util.ProgressIndicatorListenerAdapter
import com.intellij.openapi.progress.util.RelayUiToDelegateIndicator
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.UnknownSdkTracker
import com.intellij.openapi.roots.ui.configuration.UnknownSdkResolver.UnknownSdkLookup
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.util.Consumer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Predicate

private open class SdkLookupContext(private val params: SdkLookupParameters) {
  private val sdkNameCallbackExecuted = AtomicBoolean(false)
  private val sdkCallbackExecuted = AtomicBoolean(false)
  val rootProgressIndicator = ProgressIndicatorBase()

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

  init {
    val indicator = params.progressIndicator
    if (indicator is ProgressIndicatorEx) {
      rootProgressIndicator.addStateDelegate(indicator)
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

  override fun toString(): String = "SdkLookupContext($params)"
}

private val LOG = logger<SdkLookupImpl>()

internal class SdkLookupImpl : SdkLookup {
  override fun createBuilder(): SdkLookupBuilder = CommonSdkLookupBuilder { service<SdkLookup>().lookup(it) }
  override fun lookup(lookup: SdkLookupParameters): Unit = SdkLookupContextEx(lookup).lookup()
}

private class SdkLookupContextEx(lookup: SdkLookupParameters) : SdkLookupContext(lookup) {

  fun lookup() {
    ApplicationManager.getApplication().assertIsDispatchThread()

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
    }
      .filterNotNull()
      .filter { candidate -> sdkType == null || candidate.sdkType == sdkType }
      .filter { checkSdkVersion(it) }
      .forEach {
        if (waitForDownloadingSdk(it, rootProgressIndicator)) return
      }

    continueSdkLookupWithSuggestions(rootProgressIndicator)
  }

  private fun waitForDownloadingSdk(sdk: Sdk, rootProgressIndicator: ProgressIndicatorBase) : Boolean {
    val disposable = Disposer.newDisposable()
    val onDownloadCompleted = Consumer<Boolean> { onSucceeded ->
      Disposer.dispose(disposable)

      val finalSdk = when {
        onSucceeded && checkSdkHomeAndVersion(sdk) ->  sdk
        onSucceeded -> {
          LOG.warn("Just downloaded SDK: $sdk has failed the checkSdkHomeAndVersion test")
          null
        }
        else -> null
      }

      onSdkResolved(finalSdk)
    }

    val isDownloading = SdkDownloadTracker
      .getInstance()
      .tryRegisterDownloadingListener(
        sdk,
        disposable,
        rootProgressIndicator,
        onDownloadCompleted)

    if (isDownloading) {
      //it will be notified later when the download is completed
      onSdkNameResolved(sdk)
      return true
    }

    Disposer.dispose(disposable)

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

    runWithProgress(rootProgressIndicator, onCancelled = { onSdkResolved(null) }) { indicator ->
      try {
        val resolvers = UnknownSdkResolver.EP_NAME.iterable
          .mapNotNull { it.createResolver(project, indicator) }

        indicator.checkCanceled()

        if (tryLocalFix(resolvers, unknownSdk, indicator)) return@runWithProgress

        indicator.checkCanceled()

        if (tryDownloadableFix(resolvers, unknownSdk, indicator)) return@runWithProgress

        indicator.checkCanceled()

        onSdkResolved(null)
      } catch (e: ProcessCanceledException) {
        onSdkResolved(null)
        throw e
      } catch (t: Throwable) {
        LOG.warn("Failed to resolve SDK for ${this@SdkLookupContextEx}. ${t.message}", t)

        onSdkResolved(null)
      }
    }
  }

  private fun tryLocalFix(resolvers: List<UnknownSdkLookup>,
                          unknownSdk: UnknownSdk,
                          indicator: ProgressIndicator): Boolean {
    val localFix = resolvers
                     .asSequence()
                     .mapNotNull { it.proposeLocalFix(unknownSdk, indicator) }
                     .filter { onLocalSdkSuggested.invoke(it) == SdkLookupDecision.CONTINUE }
                     .filter { versionFilter?.invoke(it.versionString) != false }
                     .filter { sdkHomeFilter?.invoke(it.existingSdkHome) != false }
                     .firstOrNull() ?: return false

    indicator.checkCanceled()
    UnknownSdkTracker.configureLocalSdk(unknownSdk, localFix, onSdkResolvedConsumer)
    return true
  }

  private fun tryDownloadableFix(resolvers: List<UnknownSdkLookup>,
                                 unknownSdk: UnknownSdk,
                                 indicator: ProgressIndicator): Boolean {
    val downloadFix = resolvers
                        .asSequence()
                        .mapNotNull { it.proposeDownload(unknownSdk, indicator) }
                        .filter { onDownloadableSdkSuggested.invoke(it) == SdkLookupDecision.CONTINUE }
                        .filter { versionFilter?.invoke(it.versionString) != false }
                        .firstOrNull() ?: return false

    indicator.checkCanceled()
    UnknownSdkTracker.downloadFix(project, unknownSdk, downloadFix, onSdkNameResolvedConsumer, onSdkResolvedConsumer)
    return true
  }

  private fun runWithProgress(rootProgressIndicator: ProgressIndicatorBase,
                              onCancelled: () -> Unit,
                              action: (ProgressIndicator) -> Unit) {
    val sdkTypeName = sdkType?.presentableName ?: ProjectBundle.message("sdk")
    val title = progressMessageTitle ?: ProjectBundle.message("sdk.lookup.resolving.sdk.progress", sdkTypeName)
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true, ALWAYS_BACKGROUND) {
      override fun run(indicator: ProgressIndicator) {
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

      override fun onCancel() {
        onCancelled()
      }
    })
  }
}
