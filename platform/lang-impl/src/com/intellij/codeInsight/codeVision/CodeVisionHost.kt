// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupDefaultSettingModel
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettingsLiveModel
import com.intellij.codeInsight.codeVision.ui.CodeVisionView
import com.intellij.codeInsight.codeVision.ui.model.PlaceholderCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.RichTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.richText.RichText
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.grave.CodeVisionGrave
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.codeVision.ModificationStampUtil
import com.intellij.codeInsight.hints.settings.language.isInlaySettingsEditor
import com.intellij.codeInsight.hints.settings.showInlaySettings
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
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
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SyntaxTraverser
import com.intellij.testFramework.TestModeFlags
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.Alarm
import com.intellij.util.application
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
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

  // used externally
  @Suppress("MemberVisibilityCanBePrivate")
  val codeVisionLifetime: Lifetime = project.createLifetime()

  /**
   * Pass empty list to update ALL providers in editor
   */
  data class LensInvalidateSignal(val editor: Editor?, val providerIds: Collection<String> = emptyList())

  val invalidateProviderSignal: Signal<LensInvalidateSignal> = Signal()

  private val defaultSortedProvidersList = mutableListOf<String>()

  @Suppress("MemberVisibilityCanBePrivate")
  // Uses in Rider
  public val lifeSettingModel: CodeVisionSettingsLiveModel = CodeVisionSettingsLiveModel(codeVisionLifetime)

  var providers: List<CodeVisionProvider<*>> = CodeVisionProviderFactory.createAllProviders(project)


  open fun initialize() {
    lifeSettingModel.isRegistryEnabled.whenTrue(codeVisionLifetime) { enableCodeVisionLifetime ->
      runReadAction {
        if (project.isDisposed) return@runReadAction
        val liveEditorList = ProjectEditorLiveList(enableCodeVisionLifetime, project)
        liveEditorList.editorList.view(enableCodeVisionLifetime) { editorLifetime, editor ->
          if (isEditorApplicable(editor)) {
            subscribeForFrontendEditor(editorLifetime, editor)
          }
        }

        val viewService = project.service<CodeVisionView>()
        viewService.setPerAnchorLimits(
          CodeVisionAnchorKind.values().associateWith { (lifeSettingModel.getAnchorLimit(it) ?: defaultVisibleLenses) })


        invalidateProviderSignal.advise(enableCodeVisionLifetime) { invalidateSignal ->
          if (invalidateSignal.editor == null && invalidateSignal.providerIds.isEmpty())
            viewService.setPerAnchorLimits(
              CodeVisionAnchorKind.values().associateWith { (lifeSettingModel.getAnchorLimit(it) ?: defaultVisibleLenses) })
        }


        lifeSettingModel.visibleMetricsAboveDeclarationCount.advise(codeVisionLifetime) {
          invalidateProviderSignal.fire(LensInvalidateSignal(null, emptyList()))
        }

        lifeSettingModel.visibleMetricsNextToDeclarationCount.advise(codeVisionLifetime) {
          invalidateProviderSignal.fire(LensInvalidateSignal(null, emptyList()))
        }


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
        project.messageBus.connect(enableCodeVisionLifetime.createNestedDisposable())
          .subscribe(CodeVisionSettings.CODE_LENS_SETTINGS_CHANGED, object : CodeVisionSettings.CodeVisionSettingsListener {
            override fun groupPositionChanged(id: String, position: CodeVisionAnchorKind) {

            }

            override fun providerAvailabilityChanged(id: String, isEnabled: Boolean) {
              PsiManager.getInstance(project).dropPsiCaches()
              for (editor in EditorFactory.getInstance().allEditors) {
                ModificationStampUtil.clearModificationStamp(editor)
              }
              DaemonCodeAnalyzer.getInstance(project).restart()
              invalidateProviderSignal.fire(LensInvalidateSignal(null))
            }
          })
      }
    }

  }

  @RequiresReadLock
  suspend fun collectPlaceholders(editor: Editor, psiFile: PsiFile?): List<Pair<TextRange, CodeVisionEntry>> {
    return withTimeoutOrNull(100.milliseconds) {
      readAction {
        collectPlaceholdersInner(editor, psiFile)
      }
    } ?: emptyList()
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

  protected fun rearrangeProviders() {
    val allProviders = collectAllProviders()
    defaultSortedProvidersList.clear()
    defaultSortedProvidersList.addAll(allProviders.getTopSortedIdList())
  }

  open fun collectAllProviders(): List<Pair<String, CodeVisionProvider<*>>> {
    return providers.map { it.id to it }
  }

  private fun isEditorApplicable(editor: Editor): Boolean {
    return editor.editorKind == EditorKind.MAIN_EDITOR || editor.editorKind == EditorKind.UNTYPED
  }

  fun invalidateProvider(signal: LensInvalidateSignal) {
    invalidateProviderSignal.fire(signal)
  }


  fun getNumber(providerId: String): Int {
    if (lifeSettingModel.disabledCodeVisionProviderIds.contains(providerId)) return -1
    return defaultSortedProvidersList.indexOf(providerId)
  }


  open fun handleLensClick(editor: Editor, range: TextRange, entry: CodeVisionEntry) {
    //todo intellij statistic
    logger.trace { "Handling click for entry with id: ${entry.providerId}" }
    if (entry.providerId == settingsLensProviderId) {
      openCodeVisionSettings()
      return
    }
    val frontendProvider = providers.firstOrNull { it.id == entry.providerId }
    if (frontendProvider != null) {
      frontendProvider.handleClick(editor, range, entry)
      return
    }

    logger.trace { "No provider found with id ${entry.providerId}" }
  }

  open fun handleLensExtraAction(editor: Editor, range: TextRange, entry: CodeVisionEntry, actionId: String) {
    if (actionId == settingsLensProviderId) {
      val provider = getProviderById(entry.providerId)
      openCodeVisionSettings(provider)
      return
    }

    val frontendProvider = providers.firstOrNull { it.id == entry.providerId }
    if (frontendProvider != null) {
      frontendProvider.handleExtraAction(editor, range, actionId)
      return
    }

    logger.trace { "No provider found with id ${entry.providerId}" }
  }

  // used externally
  @Suppress("MemberVisibilityCanBePrivate")
  fun CodeVisionAnchorKind?.nullIfDefault(): CodeVisionAnchorKind? = if (this === CodeVisionAnchorKind.Default) null else this

  open fun getAnchorForEntry(entry: CodeVisionEntry): CodeVisionAnchorKind {
    val provider = getProviderById(entry.providerId) ?: return lifeSettingModel.defaultPosition.value
    return getAnchorForProvider(provider)
  }

  private fun getAnchorForProvider(provider: CodeVisionProvider<*>): CodeVisionAnchorKind {
    return lifeSettingModel.codeVisionGroupToPosition[provider.groupId].nullIfDefault()
           ?: provider.defaultAnchor.nullIfDefault()
           ?: lifeSettingModel.defaultPosition.value
  }

  private fun getPriorityForId(id: String): Int {
    return defaultSortedProvidersList.indexOf(id)
  }

  open fun getProviderById(id: String): CodeVisionProvider<*>? {
    return providers.firstOrNull { it.id == id }
  }

  fun getPriorityForEntry(entry: CodeVisionEntry): Int {
    return getPriorityForId(entry.providerId)
  }

  // we are only interested in text editors, and BRFE behaves exceptionally bad so ignore them
  private fun isAllowedFileEditor(fileEditor: FileEditor?) = fileEditor is TextEditor && fileEditor !is BaseRemoteFileEditor


  private fun subscribeForFrontendEditor(editorLifetime: Lifetime, editor: Editor) {
    val context = editor.lensContext
    if (context == null || editor.document !is DocumentImpl) return

    val calculationLifetimes = SequentialLifetimes(editorLifetime)

    var recalculateWhenVisible = false

    var previousLenses: List<Pair<TextRange, CodeVisionEntry>> = context.zombies
    val openTimeNs = System.nanoTime()
    editor.putUserData(editorTrackingStart, openTimeNs)
    val mergingQueueFront = MergingUpdateQueue(CodeVisionHost::class.simpleName!!, 100, true, null, editorLifetime.createNestedDisposable(),
                                               null, Alarm.ThreadToUse.POOLED_THREAD)
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
          project.coroutineScope.launch(Dispatchers.EDT + ModalityState.stateForComponent(editor.contentComponent).asContextElement()) {
            blockingContext {
              recalculateLenses(if (shouldRecalculateAll) emptyList() else providersToRecalculate)
            }
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

    application.messageBus.connect(editorLifetime.createNestedDisposable()).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
                                                                                      object : FileEditorManagerListener {
                                                                                        override fun selectionChanged(event: FileEditorManagerEvent) {
                                                                                          if (isAllowedFileEditor(
                                                                                              event.newEditor) && (event.newEditor as TextEditor).editor == editor && recalculateWhenVisible)
                                                                                            recalculateLenses()
                                                                                        }
                                                                                      })

    editor.document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        pokeEditor()
      }
    }, editorLifetime.createNestedDisposable())

    editorLifetime.onTermination {
      editor.project?.service<CodeVisionGrave>()?.bury(editor, context.getValidPairResult())
      context.clearLenses()
    }
  }

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
    if (lifeSettingModel.isEnabled.value.not() || context == null) {
      consumer(emptyList(), providers.map { it.id })
      return
    }

    executeOnPooledThread(calcLifetime, inTestSyncMode) {
      ProgressManager.checkCanceled()
      var results = mutableListOf<Pair<TextRange, CodeVisionEntry>>()
      val providerWhoWantToUpdate = mutableListOf<String>()

      var everyProviderReadyToUpdate = true
      val inlaySettingsEditor = isInlaySettingsEditor(editor)
      val editorOpenTimeNs = editor.getUserData(editorTrackingStart)
      providers.forEach {
        @Suppress("UNCHECKED_CAST")
        it as CodeVisionProvider<Any?>
        if (!inlaySettingsEditor && !lifeSettingModel.disabledCodeVisionProviderIds.contains(it.groupId)) {
          runSafe("shouldRecomputeForEditor for ${it.id}") {
            if (!it.shouldRecomputeForEditor(editor, precalculatedUiThings[it.id])) {
              everyProviderReadyToUpdate = false
              return@forEach
            }
          }
        }
        if (groupsToRecalculate.isNotEmpty() && !groupsToRecalculate.contains(it.id)) return@forEach
        ProgressManager.checkCanceled()
        if (project.isDisposed) return@executeOnPooledThread
        if (!inlaySettingsEditor && lifeSettingModel.disabledCodeVisionProviderIds.contains(it.groupId)) {
          if (context.hasProviderCodeVision(it.id)) {
            providerWhoWantToUpdate.add(it.id)
          }
          return@forEach
        }
        providerWhoWantToUpdate.add(it.id)
        runSafe("computeCodeVision for ${it.id}") {
          val state = it.computeCodeVision(editor, precalculatedUiThings[it.id])
          if (state.isReady) {
            results.addAll(state.result)
          }
          else if (editorOpenTimeNs == null || shouldConsiderProvider(editorOpenTimeNs)) {
            everyProviderReadyToUpdate = false
          }
        }
      }

      if (!everyProviderReadyToUpdate || providerWhoWantToUpdate.isEmpty()) {
        context.discardPending()
        return@executeOnPooledThread
      }

      val previewData = CodeVisionGroupDefaultSettingModel.isEnabledInPreview(editor)
      if (previewData == false) {
        results = results.map {
          val richText = RichText()
          richText.append(it.second.longPresentation, SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, null))
          val entry = RichTextCodeVisionEntry(it.second.providerId, richText)
          it.first to entry
        }.toMutableList()
      }

      if (!inTestSyncMode) {
        application.invokeLater({
                                  calcLifetime.executeIfAlive { consumer(results, providerWhoWantToUpdate) }
                                }, ModalityState.stateForComponent(editor.component))
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
      runnable()
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

  protected open fun openCodeVisionSettings(provider: CodeVisionProvider<*>? = null) {
    val groupId = provider?.groupId
    showInlaySettings(project, Language.ANY) {
      if (groupId == null) return@showInlaySettings it.group == InlayGroup.CODE_VISION_GROUP_NEW

      return@showInlaySettings it.group == InlayGroup.CODE_VISION_GROUP_NEW && it.id == groupId
    }
  }


  @TestOnly
  fun calculateCodeVisionSync(editor: Editor, testRootDisposable: Disposable) {
    calculateFrontendLenses(testRootDisposable.createLifetime(), editor, inTestSyncMode = true) { lenses, _ ->
      editor.lensContext?.setResults(lenses)
    }
  }

  private fun shouldConsiderProvider(editorOpenTimeNs: Long): Boolean {
    val oneMinute = 60_000_000_000
    return System.nanoTime() - editorOpenTimeNs < oneMinute
  }
}