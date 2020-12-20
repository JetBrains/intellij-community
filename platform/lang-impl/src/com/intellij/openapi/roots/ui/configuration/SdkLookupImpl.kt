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

  fun getFixListener(fix: UnknownSdkFixAction) = object : UnknownSdkFixAction.Listener {
    override fun onSdkNameResolved(sdk: Sdk) {
      this@SdkLookupContext.onSdkNameResolved(sdk)
    }

    override fun onSdkResolved(sdk: Sdk) {
      if (checkSdkHomeAndVersion(sdk)) {
        this@SdkLookupContext.onSdkResolved(sdk)
      } else {
        LOG.warn("Downloaded SDK $fix was does not pass our filters $this@SdkLookupContext")
        this@SdkLookupContext.onSdkResolved(null)
      }
    }

    override fun onResolveFailed() {
      this@SdkLookupContext.onSdkResolved(null)
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
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    object : SdkLookupContextEx(lookup) {
      override fun testSdkAndWaitForDownloadIfNeeded(sdk: Sdk, rootProgressIndicator: ProgressIndicator): Boolean {
        ApplicationManager.getApplication().assertIsNonDispatchThread()

        onSdkNameResolved(sdk)

        ///we do not has a better API on SdkDownloadTracker to wait for a download
        ///smarter than with a busy waiting. Need to re-implement the SdkDownloadTracker
        ///in a way to avoid heavy dependency on EDT (and modality state)
        try {
          while (true) {
            rootProgressIndicator.checkCanceled()
            if (!SdkDownloadTracker.getInstance().isDownloading(sdk)) break
            Thread.sleep(300)
          }
        } catch (e: InterruptedException) {
          rootProgressIndicator.checkCanceled()
          throw ProcessCanceledException()
        }

        return false
      }

      override fun runSdkResolutionUnderProgress(rootProgressIndicator: ProgressIndicatorBase, action: (ProgressIndicator) -> Unit) {
        //it is already running under progress, no need to open yet another one
        action.invoke(rootProgressIndicator)
      }

      override fun executeFix(indicator: ProgressIndicator, possibleFix: UnknownSdkFixAction) {
        possibleFix.applySuggestionBlocking(indicator)
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
        if (testLoadSdkAndWaitIfNeeded(it, rootProgressIndicator)) return
      }

    continueSdkLookupWithSuggestions(rootProgressIndicator)
  }

  private fun testLoadSdkAndWaitIfNeeded(it: Sdk,
                                         rootProgressIndicator: ProgressIndicator): Boolean {
    if (testSdkAndWaitForDownloadIfNeeded(it, rootProgressIndicator)) return true
    if (testExistingSdk(it)) return true
    return false
  }

  open fun testSdkAndWaitForDownloadIfNeeded(sdk: Sdk, rootProgressIndicator: ProgressIndicator) : Boolean {
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
    return true
  }

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

        if (possibleFix == null) {
          return@runSdkResolutionUnderProgress onSdkResolved(null)
        }

        //it could be that the suggested SDK is already registered, so we could simply return it
        //NOTE: something similar has to be done with downloading SDKs, e.g. we should replace download task with an already running one
        val sdkPrototype = possibleFix.registeredSdkPrototype
        if (sdkPrototype != null) {
          if (testLoadSdkAndWaitIfNeeded(sdkPrototype, indicator)) {
            return@runSdkResolutionUnderProgress
          } else {
            LOG.warn("The matched local SDK $possibleFix does not pass our filters in ${this@SdkLookupContextEx}")
            return@runSdkResolutionUnderProgress onSdkResolved(null)
          }
        }

        possibleFix.addSuggestionListener(getFixListener(possibleFix))
        executeFix(indicator, possibleFix)
      } catch (e: ProcessCanceledException) {
        onSdkResolved(null)
        throw e
      } catch (t: Throwable) {
        LOG.warn("Failed to resolve SDK for ${this@SdkLookupContextEx}. ${t.message}", t)

        onSdkResolved(null)
      }
    }
  }

  open fun executeFix(indicator: ProgressIndicator, possibleFix: UnknownSdkFixAction) {
    possibleFix.applySuggestionAsync(project)
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

  open fun runSdkResolutionUnderProgress(rootProgressIndicator: ProgressIndicatorBase,
                                         action: (ProgressIndicator) -> Unit) {
    val sdkTypeName = sdkType?.presentableName ?: ProjectBundle.message("sdk")
    val title = progressMessageTitle ?: ProjectBundle.message("sdk.lookup.resolving.sdk.progress", sdkTypeName)

    ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true, ALWAYS_BACKGROUND){
      override fun run(indicator: ProgressIndicator) {
        runWithBoundProgress(rootProgressIndicator, indicator, action)
      }
    })
  }

  fun runWithBoundProgress(rootProgressIndicator: ProgressIndicatorBase,
                           indicator: ProgressIndicator,
                           action: (ProgressIndicatorBase) -> Unit) {
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
}
