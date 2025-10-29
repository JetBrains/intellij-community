// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupDefaultSettingModel
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettingsLiveModel
import com.intellij.codeInsight.codeVision.ui.CodeVisionView
import com.intellij.codeInsight.codeVision.ui.model.PlaceholderCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.RichTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.richText.RichText
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.codeVision.CodeVisionProjectSettings
import com.intellij.codeInsight.hints.codeVision.ModificationStampUtil
import com.intellij.codeInsight.hints.settings.language.isInlaySettingsEditor
import com.intellij.codeInsight.hints.settings.showInlaySettings
import com.intellij.codeInsight.multiverse.EditorContextManager
import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
import com.intellij.codeWithMe.ClientId
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.*
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.BaseRemoteFileEditor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.testFramework.TestModeFlags
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.Alarm
import com.intellij.util.application
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EDT
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.jetbrains.rd.util.error
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import com.jetbrains.rd.util.reactive.Signal
import com.jetbrains.rd.util.reactive.whenTrue
import com.jetbrains.rd.util.trace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.milliseconds

open class CodeVisionHost(val project: Project) {
  companion object {
    private val logger = getLogger<CodeVisionHost>()
    const val defaultVisibleLenses: Int = 5
    const val settingsLensProviderId: String = "!Settings"

    /**
     * Flag which is enabled when executed test in [com.intellij.java.codeInsight.codeVision.CodeVisionTestCase].
     * Code vision can access different files during its work (and it is expected, e.g. references). So it is better to disable
     * particular implementations of code vision to make sure that other tests' performance is not hurt.
     */
    val isCodeVisionTestKey: Key<Boolean> = Key.create("code.vision.test")
    private val editorTrackingStart: Key<Long> = Key.create("editor.tracking.start")

    /**
     * Returns true iff we are in test in [com.intellij.java.codeInsight.codeVision.CodeVisionTestCase].
     */
    @JvmStatic
    fun isCodeLensTest(): Boolean {
      return TestModeFlags.`is`(isCodeVisionTestKey)
    }
  }

  val codeVisionLifetime: Lifetime = project.createLifetime()

  val lifeSettingModel: CodeVisionSettingsLiveModel = CodeVisionSettingsLiveModel(codeVisionLifetime)

  /**
   * Pass empty list to update ALL providers in editor
   */
  data class LensInvalidateSignal(val editor: Editor?, val providerIds: Collection<String> = emptyList())

  val invalidateProviderSignal: Signal<LensInvalidateSignal> = Signal()

  var providers: List<CodeVisionProvider<*>> = CodeVisionProviderFactory.createAllProviders(project)

  private val defaultSortedProvidersList = mutableListOf<String>()

  @RequiresEdt
  open fun initialize() {
    lifeSettingModel.isRegistryEnabled.whenTrue(codeVisionLifetime) { enableCodeVisionLifetime ->
      runReadAction {
        if (project.isDisposed) {
          return@runReadAction
        }
        subscribeEditorCreated(enableCodeVisionLifetime)
        subscribeAnchorLimitChanged(enableCodeVisionLifetime)
        subscribeMetricsPositionChanged()
        subscribeDynamicPluginLoaded(enableCodeVisionLifetime)
        subscribeCVSettingsChanged(enableCodeVisionLifetime)
      }
    }
  }

  @get:RequiresEdt
  val isInitialised: Boolean get() = _isInitialised
  private var _isInitialised = false

  @RequiresEdt
  fun finishInitialisation() {
    _isInitialised = true
  }

  open fun collectAllProviders(): List<Pair<String, CodeVisionProvider<*>>> {
    return providers.map { it.id to it }
  }

  open fun handleLensClick(editor: Editor, range: TextRange, entry: CodeVisionEntry) {
    //todo intellij statistic
    logger.trace { "Handling click for entry with id: ${entry.providerId}" }
    if (entry.providerId == settingsLensProviderId) {
      openCodeVisionSettings()
      return
    }
    firstProviderWithId(entry.providerId)?.handleClick(editor, range, entry)
  }

  open fun handleLensExtraAction(editor: Editor, range: TextRange, entry: CodeVisionEntry, actionId: String) {
    if (actionId == settingsLensProviderId) {
      val provider = getProviderById(entry.providerId)
      openCodeVisionSettings(provider)
      return
    }
    firstProviderWithId(entry.providerId)?.handleExtraAction(editor, range, actionId)
  }

  open fun getAnchorForEntry(entry: CodeVisionEntry): CodeVisionAnchorKind {
    val provider = getProviderById(entry.providerId) ?: return lifeSettingModel.defaultPosition.value
    return getAnchorForProvider(provider)
  }

  open fun getProviderById(id: String): CodeVisionProvider<*>? {
    return providers.firstOrNull { it.id == id }
  }

  suspend fun collectPlaceholders(editor: Editor, psiFile: PsiFile?): List<Pair<TextRange, CodeVisionEntry>> {
    return withTimeoutOrNull(100.milliseconds) {
      readAction {
        collectPlaceholdersInner(editor, psiFile)
      }
    } ?: emptyList()
  }

  fun invalidateProvider(signal: LensInvalidateSignal) {
    invalidateProviderSignal.fire(signal)
  }

  fun getNumber(providerId: String): Int {
    if (lifeSettingModel.disabledCodeVisionProviderIds.contains(providerId)) return -1
    return defaultSortedProvidersList.indexOf(providerId)
  }

  fun CodeVisionAnchorKind?.nullIfDefault(): CodeVisionAnchorKind? = if (this === CodeVisionAnchorKind.Default) null else this

  fun getPriorityForEntry(entry: CodeVisionEntry): Int {
    return getPriorityForId(entry.providerId)
  }

  @TestOnly
  fun calculateCodeVisionSync(editor: Editor, testRootDisposable: Disposable) {
    calculateFrontendLenses(testRootDisposable.createLifetime(), editor, inTestSyncMode = true) { lenses, _ ->
      if (EDT.isCurrentThreadEdt()) {
        ReadAction.run<Throwable> {
          editor.lensContext?.setResults(lenses)
        }
      }
      else {
        // This code runs under modal progress
        // We have no guarantees whether the scheduled event will be completed inside or outside the modal progress
        // So here we forcibly wait for its completion
        // This is a test method anyway, so it is acceptable to hold the read lock
        val future = CompletableFuture<Unit>()
        ApplicationManager.getApplication().invokeLater {
          editor.lensContext?.setResults(lenses)
          future.complete(Unit)
        }
        future.join()
      }
    }
  }

  protected open fun subscribeForDocumentChanges(editor: Editor, editorLifetime: Lifetime, onDocumentChanged: () -> Unit) {
    editor.document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        onDocumentChanged()
      }
    }, editorLifetime.createNestedDisposable())
  }

  protected open fun openCodeVisionSettings(provider: CodeVisionProvider<*>? = null) {
    val groupId = provider?.groupId
    showInlaySettings(project, Language.ANY) {
      if (groupId == null) return@showInlaySettings it.group == InlayGroup.CODE_VISION_GROUP_NEW

      return@showInlaySettings it.group == InlayGroup.CODE_VISION_GROUP_NEW && it.id == groupId
    }
  }

  protected fun rearrangeProviders() {
    val allProviders = collectAllProviders()
    defaultSortedProvidersList.clear()
    defaultSortedProvidersList.addAll(allProviders.getTopSortedIdList())
  }

  private fun subscribeForContextChanged(editor: Editor, editorLifetime: Lifetime, onContextChanged: () -> Unit) {
    val project = editor.project ?: return
    if (!isSharedSourceSupportEnabled(project)) return
    project.messageBus.connect(editorLifetime.createNestedDisposable()).subscribe(EditorContextManager.topic, object : EditorContextManager.ChangeEventListener {
      override fun editorContextsChanged(event: EditorContextManager.ChangeEvent) {
        if (editor == event.editor) {
          application.invokeLater {
            onContextChanged()
          }
        }
      }
    })
  }

  private fun subscribeEditorCreated(enableCodeVisionLifetime: Lifetime) {
    val liveEditorList = ProjectEditorLiveList(enableCodeVisionLifetime, project)
    liveEditorList.editorList.view(enableCodeVisionLifetime) { editorLifetime, editor ->
      if (isEditorApplicable(editor)) {
        onEditorCreated(editorLifetime, editor)
      }
    }
  }

  private fun subscribeAnchorLimitChanged(enableCodeVisionLifetime: Lifetime) {
    val viewService = project.service<CodeVisionView>()
    viewService.setPerAnchorLimits(
      CodeVisionAnchorKind.entries.associateWith { (lifeSettingModel.getAnchorLimit(it) ?: defaultVisibleLenses) })

    invalidateProviderSignal.advise(enableCodeVisionLifetime) { invalidateSignal ->
      if (invalidateSignal.editor == null && invalidateSignal.providerIds.isEmpty())
        viewService.setPerAnchorLimits(
          CodeVisionAnchorKind.entries.associateWith { (lifeSettingModel.getAnchorLimit(it) ?: defaultVisibleLenses) })
    }
  }

  private fun subscribeMetricsPositionChanged() {
    lifeSettingModel.visibleMetricsAboveDeclarationCount.advise(codeVisionLifetime) {
      invalidateProviderSignal.fire(LensInvalidateSignal(null, emptyList()))
    }

    lifeSettingModel.visibleMetricsNextToDeclarationCount.advise(codeVisionLifetime) {
      invalidateProviderSignal.fire(LensInvalidateSignal(null, emptyList()))
    }
  }

  private fun subscribeDynamicPluginLoaded(enableCodeVisionLifetime: Lifetime) {
    rearrangeProviders()
    project.messageBus.connect(enableCodeVisionLifetime.createNestedDisposable())
      .subscribe(DynamicPluginListener.TOPIC,
                 object : DynamicPluginListener {
                   private fun recollectAndRearrangeProviders() {
                     providers = CodeVisionProviderFactory.createAllProviders(
                       project)
                     rearrangeProviders()
                   }

                   override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
                     recollectAndRearrangeProviders()
                   }

                   override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor,
                                               isUpdate: Boolean) {
                     recollectAndRearrangeProviders()
                   }
                 })
  }

  private fun subscribeCVSettingsChanged(enableCodeVisionLifetime: Lifetime) {
    project.messageBus.connect(enableCodeVisionLifetime.createNestedDisposable())
      .subscribe(CodeVisionSettings.CODE_LENS_SETTINGS_CHANGED, object : CodeVisionSettings.CodeVisionSettingsListener {
        override fun groupPositionChanged(id: String, position: CodeVisionAnchorKind) {

        }

        override fun providerAvailabilityChanged(id: String, isEnabled: Boolean) {
          PsiManager.getInstance(project).dropPsiCaches()
          for (editor in EditorFactory.getInstance().allEditors) {
            ModificationStampUtil.clearModificationStamp(editor)
          }
          DaemonCodeAnalyzer.getInstance(project).restart("CodeVisionHost.providerAvailabilityChanged $id")
          invalidateProviderSignal.fire(LensInvalidateSignal(null))
        }
      })
  }

  private fun collectPlaceholdersInner(editor: Editor, psiFile: PsiFile?): List<Pair<TextRange, CodeVisionEntry>> {
    if (!lifeSettingModel.isEnabledWithRegistry.value) return emptyList()
    val project = editor.project ?: return emptyList()
    if (psiFile != null && psiFile.virtualFile != null) {
      if (ProjectFileIndex.getInstance(project).isInLibrarySource(psiFile.virtualFile)) return emptyList()
    }
    val bypassBasedCollectors = ArrayList<Pair<BypassBasedPlaceholderCollector, CodeVisionProvider<*>>>()
    val placeholders = ArrayList<Pair<TextRange, CodeVisionEntry>>()
    val settings = CodeVisionSettings.getInstance()
    for (provider in providers) {
      if (!settings.isProviderEnabled(provider.groupId)) continue
      if (getAnchorForProvider(provider) != CodeVisionAnchorKind.Top) continue
      val placeholderCollector: CodeVisionPlaceholderCollector = provider.getPlaceholderCollector(editor, psiFile) ?: continue
      if (placeholderCollector is BypassBasedPlaceholderCollector) {
        bypassBasedCollectors.add(placeholderCollector to provider)
      }
      else if (placeholderCollector is GenericPlaceholderCollector) {
        for (placeholderRange in placeholderCollector.collectPlaceholders(editor)) {
          placeholders.add(placeholderRange to PlaceholderCodeVisionEntry(provider.id))
        }
      }
    }

    if (bypassBasedCollectors.isNotEmpty()) {
      val traverser = SyntaxTraverser.psiTraverser(psiFile)
      for (element in traverser) {
        for ((collector, provider) in bypassBasedCollectors) {
          for (placeholderRange in collector.collectPlaceholders(element, editor)) {
            placeholders.add(placeholderRange to PlaceholderCodeVisionEntry(provider.id))
          }
        }
      }
    }

    return placeholders
  }

  private fun getAnchorForProvider(provider: CodeVisionProvider<*>): CodeVisionAnchorKind {
    return lifeSettingModel.codeVisionGroupToPosition[provider.groupId].nullIfDefault()
           ?: provider.defaultAnchor.nullIfDefault()
           ?: lifeSettingModel.defaultPosition.value
  }

  private fun getPriorityForId(id: String): Int {
    return defaultSortedProvidersList.indexOf(id)
  }

  private fun onEditorCreated(editorLifetime: Lifetime, editor: Editor) {
    val context = editor.lensContext
    if (context == null || editor.document !is DocumentImpl) return

    val calculationLifetimes = SequentialLifetimes(editorLifetime)

    var recalculateWhenVisible = false

    var previousLenses: List<Pair<TextRange, CodeVisionEntry>> = context.zombies
    val openTimeNs = System.nanoTime()
    editor.putUserData(editorTrackingStart, openTimeNs)
    val mergingQueueFront = MergingUpdateQueue(
      CodeVisionHost::class.simpleName!!,
      300,
      true,
      null,
      editorLifetime.createNestedDisposable(),
      null,
      Alarm.ThreadToUse.POOLED_THREAD
    )
    mergingQueueFront.isPassThrough = false
    var calcRunning = false

    fun recalculateLenses(groupToRecalculate: Collection<String> = emptyList()) {
      val editorManager = FileEditorManager.getInstance(project)
      if (!isInlaySettingsEditor(editor) && !editorManager.selectedEditors.any {
          isAllowedFileEditor(it) && (it as TextEditor).editor == editor
        }) {
        recalculateWhenVisible = true
        return
      }
      recalculateWhenVisible = false
      if (calcRunning && groupToRecalculate.isNotEmpty()) {
        return recalculateLenses(emptyList())
      }
      calcRunning = true
      val lt = calculationLifetimes.next()
      calculateFrontendLenses(lt, editor, groupToRecalculate) { lenses, providersToUpdate ->
        val newLenses = previousLenses.filter { !providersToUpdate.contains(it.second.providerId) } + lenses

        context.setResults(newLenses)
        previousLenses = newLenses
        calcRunning = false
      }
    }

    fun pokeEditor(providersToRecalculate: Collection<String> = emptyList()) {
      context.notifyPendingLenses()
      val shouldRecalculateAll = mergingQueueFront.isEmpty.not()
      mergingQueueFront.cancelAllUpdates()
      mergingQueueFront.queue(object : Update("") {
        override fun run() {
          val modalityState = ModalityState.stateForComponent(editor.contentComponent).asContextElement()
          (project as ComponentManagerEx).getCoroutineScope().launch(Dispatchers.EDT + modalityState + ClientId.coroutineContext()) {
            recalculateLenses(if (shouldRecalculateAll) emptyList() else providersToRecalculate)
          }
        }
      })
    }

    invalidateProviderSignal.advise(editorLifetime) {
      if (it.editor == null || it.editor === editor) {
        pokeEditor(it.providerIds)
      }
    }

    context.notifyPendingLenses()
    recalculateLenses()

    application.messageBus.connect(editorLifetime.createNestedDisposable()).subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      object : FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) {
          if (isAllowedFileEditor(event.newEditor) &&
              (event.newEditor as TextEditor).editor == editor &&
              recalculateWhenVisible)
            recalculateLenses()
        }
      }
    )

    subscribeForDocumentChanges(editor, editorLifetime) {
      pokeEditor()
    }

    subscribeForContextChanged(editor, editorLifetime) {
      pokeEditor()
    }

    editorLifetime.onTermination {
      context.clearLenses()
    }
  }

  // we are only interested in text editors, and BRFE behaves exceptionally bad so ignore them
  private fun isAllowedFileEditor(fileEditor: FileEditor?) = fileEditor is TextEditor && fileEditor !is BaseRemoteFileEditor

  private fun calculateFrontendLenses(calcLifetime: Lifetime,
                                      editor: Editor,
                                      groupsToRecalculate: Collection<String> = emptyList(),
                                      inTestSyncMode: Boolean = false,
                                      consumer: (List<Pair<TextRange, CodeVisionEntry>>, List<String>) -> Unit) {
    val precalculatedUiThings = providers.associate {
      if (groupsToRecalculate.isNotEmpty() && !groupsToRecalculate.contains(it.id)) return@associate it.id to null
      it.id to it.precomputeOnUiThread(editor)
    }
    val context = editor.lensContext
    // dropping all lenses if CV disabled
    if (context == null
        || !lifeSettingModel.isEnabled.value
        || !CodeVisionProjectSettings.getInstance(project).isEnabledForProject()) {
      consumer(emptyList(), providers.map { it.id })
      return
    }

    executeOnPooledThread(calcLifetime, inTestSyncMode) {
      ProgressManager.checkCanceled()
      val isEditorInsideSettingsPanel = isInlaySettingsEditor(editor)
      val editorOpenTimeNs = editor.getUserData(editorTrackingStart)
      val modCount = modificationCount(editor)

      var results = mutableListOf<Pair<TextRange, CodeVisionEntry>>()
      val providerWhoWantToUpdate = mutableListOf<String>()
      var everyProviderReadyToUpdate = true
      for (p in providers) {
        val provider = p as CodeVisionProvider<Any?>
        val providerId = provider.id
        val isGroupEnabled = !lifeSettingModel.disabledCodeVisionProviderIds.contains(provider.groupId)

        if (isGroupEnabled && !isEditorInsideSettingsPanel) {
          val shouldRecompute = shouldRecomputeForEditor(editor, provider, precalculatedUiThings)
          if (!shouldRecompute) {
            everyProviderReadyToUpdate = false
            continue
          }
        }

        if (groupsToRecalculate.isNotEmpty() && !groupsToRecalculate.contains(providerId)) {
          continue
        }

        ProgressManager.checkCanceled()
        if (project.isDisposed) {
          return@executeOnPooledThread
        }

        if (!isGroupEnabled && !isEditorInsideSettingsPanel) {
          if (context.hasProviderCodeVision(providerId)) {
            providerWhoWantToUpdate.add(providerId)
          }
          continue
        }

        providerWhoWantToUpdate.add(providerId)
        runSafe("computeCodeVision for $providerId") {
          val state = provider.computeCodeVision(editor, precalculatedUiThings[providerId])
          if (state.isReady) {
            results.addAll(state.result)
          }
          else if (editorOpenTimeNs == null || shouldConsiderProvider(editorOpenTimeNs)) {
            everyProviderReadyToUpdate = false
          }
        }

        if (modCount != modificationCount(editor)) {
          // psi or document changed, aborting current run as outdated
          return@executeOnPooledThread
        }
      }

      if (!everyProviderReadyToUpdate || providerWhoWantToUpdate.isEmpty()) {
        context.discardPending()
        return@executeOnPooledThread
      }

      val previewData = CodeVisionGroupDefaultSettingModel.isEnabledInPreview(editor)
      if (previewData == false) {
        results = enrichTextWithStrikeoutLine(results)
      }

      if (!inTestSyncMode) {
        application.invokeLater(
          Runnable {
            calcLifetime.executeIfAlive {
              if (modCount == modificationCount(editor)) {
                consumer(results, providerWhoWantToUpdate)
              }
            }
          },
          ModalityState.stateForComponent(editor.component)
        )
      }
      else {
        consumer(results, providerWhoWantToUpdate)
      }
    }
  }

  private fun executeOnPooledThread(lifetime: Lifetime, inTestSyncMode: Boolean, runnable: () -> Unit): ProgressIndicator {
    val indicator = EmptyProgressIndicator()
    indicator.start()

    if (!inTestSyncMode) {
      CompletableFuture.runAsync(
        { ProgressManager.getInstance().runProcess(runnable, indicator) },
        AppExecutorUtil.getAppExecutorService()
      )

      lifetime.onTerminationIfAlive {
        if (indicator.isRunning) indicator.cancel()
      }
    }
    else {
      ActionUtil.underModalProgress(project, "") { runnable() }
    }

    return indicator
  }

  private inline fun runSafe(name: String, block: () -> Unit) {
    try {
      block()
    }
    catch (e: Exception) {
      if (e is ControlFlowException) throw e

      logger.error("Exception during $name", e)
    }
  }

  private fun isEditorApplicable(editor: Editor): Boolean {
    return editor.editorKind == EditorKind.MAIN_EDITOR || editor.editorKind == EditorKind.UNTYPED
  }

  private fun shouldConsiderProvider(editorOpenTimeNs: Long): Boolean {
    val oneMinute = 60_000_000_000
    return System.nanoTime() - editorOpenTimeNs < oneMinute
  }

  private fun shouldRecomputeForEditor(editor: Editor, provider: CodeVisionProvider<Any?>, uiThings: Map<String, Any?>): Boolean {
    runSafe("shouldRecomputeForEditor for ${provider.id}") {
      return provider.shouldRecomputeForEditor(editor, uiThings[provider.id])
    }
    return true
  }

  private fun enrichTextWithStrikeoutLine(results: List<Pair<TextRange, CodeVisionEntry>>): MutableList<Pair<TextRange, CodeVisionEntry>> {
    return results.map {
      val richText = RichText()
      richText.append(it.second.longPresentation, SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, null))
      val entry = RichTextCodeVisionEntry(it.second.providerId, richText)
      it.first to entry
    }.toMutableList()
  }

  private fun firstProviderWithId(providerId: String): CodeVisionProvider<*>? {
    val provider = providers.firstOrNull { it.id == providerId }
    if (provider == null) {
      logger.trace { "No provider found with id $providerId" }
    }
    return provider
  }

  private fun modificationCount(editor: Editor): Long {
    return PsiModificationTracker.getInstance(editor.project).modificationCount + editor.document.modificationStamp
  }
}
