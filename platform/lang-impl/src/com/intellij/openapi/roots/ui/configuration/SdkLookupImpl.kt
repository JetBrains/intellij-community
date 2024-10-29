// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.UnknownMissingSdk
import com.intellij.openapi.projectRoots.impl.UnknownSdkFixAction
import com.intellij.openapi.roots.ui.configuration.UnknownSdkResolver.UnknownSdkLookup
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.util.Consumer
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Predicate
import java.util.function.Supplier

private open class SdkLookupContext(private val params: SdkLookupParameters) {
  private val sdkNameCallbackExecuted = AtomicBoolean(false)
  private val sdkCallbackExecuted = AtomicBoolean(false)

  val sdkName = params.sdkName
  val sdkType = params.sdkType
  val testSdkSequence = params.testSdkSequence
  val project = params.project
  val progressMessageTitle = params.progressMessageTitle

  val sdkHomeFilter = params.sdkHomeFilter
  val versionFilter = params.versionFilter
  val onDownloadingSdkDetected = params.onDownloadingSdkDetected
  val onBeforeSdkSuggestionStarted = params.onBeforeSdkSuggestionStarted
  val onLocalSdkSuggested = params.onLocalSdkSuggested
  val onDownloadableSdkSuggested = params.onDownloadableSdkSuggested
  val onSdkFixResolved = params.onSdkFixResolved

  val onSdkNameResolvedConsumer = Consumer<Sdk?> { onSdkNameResolved(it) }
  val onSdkResolvedConsumer = Consumer<Sdk?> { onSdkResolved(it) }

  fun resolveProgressIndicator(): ProgressIndicator {
    val indicator = params.progressIndicator
    if (indicator != null) return indicator
    return ProgressIndicatorBase()
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
    }
    else {
      params.onSdkResolved.invoke(sdk)
    }
  }

  fun checkSdkHomeAndVersion(sdk: Sdk?): Boolean {
    if (sdk == null) return false

    val sdkHome = runCatching { sdk.homePath }.getOrNull() ?: return false
    return params.sdkHomeFilter?.invoke(sdkHome) != false && checkSdkVersion(sdk)
  }

  fun checkSdkVersion(sdk: Sdk?): Boolean {
    if (sdk == null) return false
    if (sdkType != null && sdk.sdkType != sdkType) return false

    val versionString = runCatching { sdk.versionString }.getOrNull() ?: return false
    return params.versionFilter?.invoke(versionString) != false
  }

  fun getFixListener(fix: UnknownSdkFixAction) = object : UnknownSdkFixAction.Listener {
    override fun onSdkNameResolved(sdk: Sdk) {
      this@SdkLookupContext.onSdkNameResolved(sdk)
    }

    override fun onSdkResolved(sdk: Sdk) {
      if (checkSdkHomeAndVersion(sdk)) {
        this@SdkLookupContext.onSdkResolved(sdk)
      }
      else {
        LOG.warn("Downloaded SDK $fix was does not pass our filters $this@SdkLookupContext")
        this@SdkLookupContext.onSdkResolved(null)
      }
    }

    override fun onResolveFailed() {
      this@SdkLookupContext.onSdkResolved(null)
    }

    override fun onResolveCancelled() {
      this@SdkLookupContext.onSdkResolved(null)
    }
  }

  override fun toString(): String = "SdkLookupContext($params)"
}

private val LOG = logger<SdkLookupImpl>()

@VisibleForTesting
@ApiStatus.Internal
class SdkLookupImpl : SdkLookup {
  override fun createBuilder(): SdkLookupBuilder = CommonSdkLookupBuilder(lookup = { service<SdkLookup>().lookup(it) })

  override fun lookup(lookup: SdkLookupParameters) {
    SdkLookupContextEx(lookup).lookup()
  }

  @RequiresBackgroundThread
  override fun lookupBlocking(lookup: SdkLookupParameters) {
    object : SdkLookupContextEx(lookup) {
      override fun doWaitSdkDownloadToComplete(sdk: Sdk, rootProgressIndicator: ProgressIndicator): () -> Boolean {
        LOG.warn("It is not possible to wait for SDK download to complete in blocking execution mode. " +
                 "Use another " + SdkLookupDownloadDecision::class.simpleName)

        return {
          ThreadingAssertions.assertBackgroundThread()
          onSdkNameResolved(sdk)

          ///we do not have a better API on SdkDownloadTracker to wait for a download
          ///smarter than with a busy waiting. Need to re-implement the SdkDownloadTracker
          ///in a way to avoid heavy dependency on EDT (and modality state)
          try {
            while (true) {
              rootProgressIndicator.checkCanceled()
              if (!SdkDownloadTracker.getInstance().isDownloading(sdk)) break
              Thread.sleep(300)
            }
          }
          catch (_: InterruptedException) {
            rootProgressIndicator.checkCanceled()
            throw ProcessCanceledException()
          }

          false
        }
      }

      override fun runSdkResolutionUnderProgress(rootProgressIndicator: ProgressIndicator, action: (ProgressIndicator) -> Unit) {
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
  val lookupReason = lookup.lookupReason

  fun lookup() {
    val rootProgressIndicator = resolveProgressIndicator()

    val namedSdk = sdkName?.let {
      ApplicationManager.getApplication().runReadAction(Computable {
        when (sdkType) {
          null -> ProjectJdkTable.getInstance().findJdk(sdkName)
          else -> ProjectJdkTable.getInstance().findJdk(sdkName, sdkType.name)
        }
      })
    }

    if (trySdk(namedSdk, rootProgressIndicator)) {
      return
    }

    for (sdk: Sdk? in SdkDownloadTracker.getInstance().findDownloadingSdks(sdkName)) {
      if (trySdk(sdk, rootProgressIndicator)) return
    }

    for (sdk in testSdkSequence) {
      if (trySdk(sdk, rootProgressIndicator)) return
    }

    continueSdkLookupWithSuggestions(rootProgressIndicator)
  }

  private fun trySdk(sdk: Sdk?, rootProgressIndicator: ProgressIndicator): Boolean {
    rootProgressIndicator.checkCanceled()
    if (sdk == null) return false
    return testLoadSdkAndWaitIfNeeded(sdk, rootProgressIndicator)
  }

  private fun testLoadSdkAndWaitIfNeeded(
    sdk: Sdk,
    rootProgressIndicator: ProgressIndicator,
  ): Boolean {

    if (!checkSdkVersion(sdk)) return false
    if (testSdkAndWaitForDownloadIfNeeded(sdk, rootProgressIndicator)) return true
    if (testExistingSdk(sdk)) return true
    return false
  }

  fun testSdkAndWaitForDownloadIfNeeded(sdk: Sdk, rootProgressIndicator: ProgressIndicator): Boolean {
    if (!SdkDownloadTracker.getInstance().isDownloading(sdk)) return false

    //  we need to make sure there is no race conditions,
    // the SdkDownloadTracker has to use WriteAction to apply changes
    val action: () -> Boolean = invokeAndWaitIfNeeded {
      //double-checked to avoid
      if (!SdkDownloadTracker.getInstance().isDownloading(sdk)) return@invokeAndWaitIfNeeded { false }

      onSdkNameResolved(sdk)

      when (onDownloadingSdkDetected(sdk)) {
        SdkLookupDownloadDecision.WAIT -> doWaitSdkDownloadToComplete(sdk, rootProgressIndicator)
        SdkLookupDownloadDecision.SKIP -> {
          { false }
        }
        SdkLookupDownloadDecision.STOP -> {
          { onSdkResolved(null); true }
        }
      }
    }

    return action()
  }

  open fun doWaitSdkDownloadToComplete(sdk: Sdk, rootProgressIndicator: ProgressIndicator): () -> Boolean {
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

    val isDownloading =
      SdkDownloadTracker
        .getInstance()
        .tryRegisterDownloadingListener(
          sdk,
          disposable,
          rootProgressIndicator,
          onDownloadCompleted)

    if (!isDownloading) {
      Disposer.dispose(disposable)
      return { false }
    }

    //it will be notified later when the download is completed
    return { true }
  }

  fun testExistingSdk(sdk: Sdk): Boolean {
    //it could be the case with an ordinary SDK, it may not pass the test below
    if (!checkSdkHomeAndVersion(sdk)) return false

    onSdkResolved(sdk)
    return true
  }

  private fun continueSdkLookupWithSuggestions(rootProgressIndicator: ProgressIndicator) {
    if (sdkType == null) {
      //it is not possible to suggest everything, if [sdkType] is not specified
      onSdkResolved(null)
      return
    }

    if (onBeforeSdkSuggestionStarted() == SdkLookupDecision.STOP) {
      onSdkResolved(null)
      return
    }

    val unknownSdk = object : UnknownSdk {
      val versionPredicate = versionFilter?.let(::toVersionPredicate)
      val homePredicate = sdkHomeFilter?.let(::toHomePredicate)

      override fun getSdkName() = this@SdkLookupContextEx.sdkName
      override fun getSdkType(): SdkType = this@SdkLookupContextEx.sdkType
      override fun getSdkVersionStringPredicate() = versionPredicate
      override fun getSdkHomePredicate() = homePredicate

      override fun toString() = buildString {
        append("SdkLookup{${sdkType.presentableName}")

        sdkName?.let {
          append(", name=$it")
        }

        versionPredicate?.let {
          //it makes no sense to call .toString on Kotlin Lambdas
          append(", withVersionFilter")
        }

        sdkHomeFilter?.let {
          //it makes no sense to call .toString on Kotlin Lambdas
          append(", withSdkHomeFilter")
        }

        append("}")
      }
    }

    runSdkResolutionUnderProgress(rootProgressIndicator) { indicator ->
      try {
        val resolvers = UnknownSdkResolver.EP_NAME.lazySequence()
          .mapNotNull { it.createResolver(project, indicator) }
          .toList()

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
        if (sdkPrototype != null && sdkPrototype in runReadAction { ProjectJdkTable.getInstance().allJdks }) {
          if (testLoadSdkAndWaitIfNeeded(sdkPrototype, indicator)) {
            return@runSdkResolutionUnderProgress
          }
          else {
            LOG.warn("The matched local SDK $possibleFix does not pass our filters in ${this@SdkLookupContextEx}")
            return@runSdkResolutionUnderProgress onSdkResolved(null)
          }
        }

        if (onSdkFixResolved(possibleFix) != SdkLookupDecision.CONTINUE) {
          return@runSdkResolutionUnderProgress onSdkResolved(null)
        }

        possibleFix.addSuggestionListener(getFixListener(possibleFix))
        executeFix(indicator, possibleFix)
      }
      catch (e: ProcessCanceledException) {
        onSdkResolved(null)
        throw e
      }
      catch (t: Throwable) {
        LOG.warn("Failed to resolve SDK for ${this@SdkLookupContextEx}. ${t.message}", t)

        onSdkResolved(null)
      }
    }
  }

  private fun toHomePredicate(sdkHomeFilter: (String) -> Boolean): Predicate<String> {
    return object : Predicate<String> {
      override fun test(t: String) = sdkHomeFilter.invoke(t)
      override fun toString() = sdkHomeFilter.toString()
    }
  }

  private fun toVersionPredicate(versionFilter: (String) -> Boolean): Predicate<String> {
    return object : Predicate<String> {
      override fun test(t: String) = versionFilter.invoke(t)
      override fun toString() = versionFilter.toString()
    }
  }

  open fun executeFix(indicator: ProgressIndicator, possibleFix: UnknownSdkFixAction) {
    possibleFix.applySuggestionAsync(project)
  }

  private fun resolveLocalFix(
    resolvers: List<UnknownSdkLookup>,
    unknownSdk: UnknownSdk,
    indicator: ProgressIndicator,
  ) = indicator.withPushPop {
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

  private fun resolveDownloadFix(
    resolvers: List<UnknownSdkLookup>,
    unknownSdk: UnknownSdk,
    indicator: ProgressIndicator,
  ) = indicator.withPushPop {
    indicator.text = ProjectBundle.message("progress.text.looking.for.downloadable.sdks")
    resolvers
      .asSequence()
      .onEach { indicator.checkCanceled() }
      .mapNotNull { it.proposeDownload(unknownSdk, indicator, lookupReason) }
      .filter { versionFilter?.invoke(it.versionString) != false }
      .onEach { indicator.checkCanceled() }
      .filter { onDownloadableSdkSuggested.invoke(it) == SdkLookupDecision.CONTINUE }
      .firstOrNull()
  }

  open fun runSdkResolutionUnderProgress(rootProgressIndicator: ProgressIndicator, action: (ProgressIndicator) -> Unit) {
    val sdkTypeName = sdkType?.presentableName ?: ProjectBundle.message("sdk")
    val title = progressMessageTitle ?: ProjectBundle.message("sdk.lookup.resolving.sdk.progress", sdkTypeName)

    ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true, ALWAYS_BACKGROUND) {
      override fun run(indicator: ProgressIndicator) {
        runUnderNestedProgressAndRelayMessages(rootProgressIndicator, indicator, action = action)
      }
    })
  }
}
