package com.intellij.codeInsight.codeVision

import com.intellij.codeInsight.codeVision.settings.CodeVisionSettingsLiveModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createLifetime
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.reactive.Signal
import com.intellij.codeInsight.codeVision.ui.CodeVisionView
import com.intellij.codeInsight.hints.settings.InlaySettingsConfigurable
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.BaseRemoteFileEditor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.util.TextRange
import com.intellij.util.application
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rd.util.reactive.whenTrue
import com.jetbrains.rd.util.trace
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CompletableFuture

open class CodeVisionHost(val project: Project) {
  companion object {
    private val logger = getLogger<CodeVisionHost>()
    fun getInstance(project: Project): CodeVisionHost = project.getService(CodeVisionHost::class.java)

    const val defaultVisibleLenses = 5
    const val settingsLensProviderId = "!Settings"
    const val moreLensProviderId = "!More"
  }

  protected val codeVisionLifetime = project.createLifetime()

  data class LensInvalidateSignal(val editor: Editor?, val providerId: String?)

  val invalidateProviderSignal: Signal<LensInvalidateSignal> = Signal()

  private val defaultSortedProvidersList = mutableListOf<String>()

  protected val lifeSettingModel = CodeVisionSettingsLiveModel(codeVisionLifetime)

  var providers: List<CodeVisionProvider<*>> = CodeVisionProviderFactory.createAllProviders(project)


  init {
    lifeSettingModel.isEnabledWithRegistry.whenTrue(codeVisionLifetime) { enableCodeVisionLifetime ->
      val liveEditorList = ProjectEditorLiveList(enableCodeVisionLifetime, project)
      liveEditorList.editorList.view(enableCodeVisionLifetime) { editorLifetime, editor ->
        if (isEditorApplicable(editor))
          subscribeForFrontendEditor(editorLifetime, editor)
      }

      val viewService = ServiceManager.getService(project, CodeVisionView::class.java)
      viewService.setPerAnchorLimits(
        CodeVisionAnchorKind.values().associateWith { (lifeSettingModel.getAnchorLimit(it) ?: defaultVisibleLenses) })


      invalidateProviderSignal.advise(enableCodeVisionLifetime) { invalidateSignal ->
        if (invalidateSignal.editor == null && invalidateSignal.providerId == null)
          viewService.setPerAnchorLimits(
            CodeVisionAnchorKind.values().associateWith { (lifeSettingModel.getAnchorLimit(it) ?: defaultVisibleLenses) })

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
    }
  }

  protected fun rearrangeProviders() {
    val allProviders = collectAllProviders()
    defaultSortedProvidersList.clear()
    defaultSortedProvidersList.addAll(allProviders.getTopSortedIdList())
  }

  protected open fun collectAllProviders(): List<Pair<String, CodeVisionProvider<*>>> {
    return providers.map { it.id to it }
  }

  private fun isEditorApplicable(editor: Editor): Boolean {
    return editor.editorKind == EditorKind.MAIN_EDITOR || editor.editorKind == EditorKind.UNTYPED
  }


  fun getNumber(providerId: String): Int {
    if (lifeSettingModel.disabledCodeVisionProviderIds.contains(providerId)) return -1
    return defaultSortedProvidersList.indexOf(providerId)
  }


  open fun handleLensClick(editor: Editor, range: TextRange, entry: CodeVisionEntry) {
    //todo intellij statistic
    if (entry.providerId == settingsLensProviderId) {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, InlaySettingsConfigurable::class.java)
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
    val frontendProvider = providers.firstOrNull { it.id == entry.providerId }
    if (frontendProvider != null) {
      frontendProvider.handleExtraAction(editor, range, actionId)
      return
    }

    logger.trace { "No provider found with id ${entry.providerId}" }
  }

  protected fun CodeVisionAnchorKind?.nullIfDefault(): CodeVisionAnchorKind? = if (this === CodeVisionAnchorKind.Default) null else this


  open fun getAnchorForEntry(entry: CodeVisionEntry): CodeVisionAnchorKind {
    val provider = getProviderById(entry.providerId) ?: return lifeSettingModel.defaultPosition.value
    return lifeSettingModel.codeVisionGroupToPosition[provider.name].nullIfDefault() ?: lifeSettingModel.defaultPosition.value
  }

  private fun getPriorityForId(id: String): Int {
    return defaultSortedProvidersList.indexOf(id)
  }

  protected open fun getProviderById(id: String): CodeVisionProvider<*>? {
    return providers.firstOrNull { it.id == id }
  }

  fun getPriorityForEntry(entry: CodeVisionEntry): Int {
    return getPriorityForId(entry.providerId)
  }

  // we are only interested in text editors, and BRFE behaves exceptionally bad so ignore them
  private fun isAllowedFileEditor(fileEditor: FileEditor?) = fileEditor is TextEditor && fileEditor !is BaseRemoteFileEditor


  private fun subscribeForFrontendEditor(editorLifetime: Lifetime, editor: Editor) {
    if (editor.document !is DocumentImpl) return

    val calculationLifetimes = SequentialLifetimes(editorLifetime)

    val editorManager = FileEditorManager.getInstance(project)
    var recalculateWhenVisible = false

    var previousLenses: List<Pair<TextRange, CodeVisionEntry>> = ArrayList()
    val mergingQueueFront = MergingUpdateQueue(CodeVisionHost::class.simpleName!!, 100, true, null, editorLifetime.createNestedDisposable())
    mergingQueueFront.isPassThrough = false
    var calcRunning = false

    fun recalculateLenses(single: String? = null) {
      if (!editorManager.selectedEditors.any { isAllowedFileEditor(it) && (it as TextEditor).editor == editor }) {
        recalculateWhenVisible = true
        return
      }
      recalculateWhenVisible = false
      if (calcRunning && single != null)
        return recalculateLenses(null)
      calcRunning = true
      val lt = calculationLifetimes.next()
      calculateFrontendLenses(lt, editor, single) { lenses ->
        val newLenses = if (single != null) {
          previousLenses.filter { it.second.providerId != single } + lenses
        }
        else {
          lenses
        }
        editor.lensContextOrThrow.setResults(newLenses)
        previousLenses = newLenses
        calcRunning = false
      }
    }

    fun pokeEditor(providerId: String? = null) {
      editor.lensContextOrThrow.notifyPendingLenses()
      val shouldRecalculateAll = mergingQueueFront.isEmpty.not()
      mergingQueueFront.cancelAllUpdates()
      mergingQueueFront.queue(object : Update("") {
        override fun run() {
          recalculateLenses(if (shouldRecalculateAll) null else providerId)
        }
      })
    }

    invalidateProviderSignal.advise(editorLifetime) {
      if (it.editor == null || it.editor === editor) {
        pokeEditor(it.providerId)
      }
    }

    editor.lensContextOrThrow.notifyPendingLenses()
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

    editorLifetime.onTermination { editor.lensContextOrThrow.clearLenses() }
  }

  private fun calculateFrontendLenses(calcLifetime: Lifetime,
                                      editor: Editor,
                                      singleLensProvider: String? = null,
                                      inTestSyncMode: Boolean = false,
                                      consumer: (List<Pair<TextRange, CodeVisionEntry>>) -> Unit) {
    val precalculatedUiThings = providers.associate {
      if (singleLensProvider != null && singleLensProvider != it.id) return@associate it.id to null
      it.id to it.precomputeOnUiThread(editor)
    }
    executeOnPooledThread(calcLifetime, inTestSyncMode) {
      ProgressManager.checkCanceled()
      val results = mutableListOf<Pair<TextRange, CodeVisionEntry>>()
      var shouldResetCodeVision = true
      providers.forEach {
        @Suppress("UNCHECKED_CAST")
        it as CodeVisionProvider<Any?>
        if (singleLensProvider != null && singleLensProvider != it.id) return@forEach
        ProgressManager.checkCanceled()
        if (project.isDisposed) return@executeOnPooledThread
        if (lifeSettingModel.disabledCodeVisionProviderIds.contains(it.groupId)) {
          if (editor.lensContextOrThrow.hasProviderCodeVision(it.groupId)) {
            shouldResetCodeVision = false
          }
          return@forEach
        }
        if(!it.shouldRecomputeForEditor(editor, precalculatedUiThings[it.id])) return@forEach
        shouldResetCodeVision = false
        val result = it.computeForEditor(editor, precalculatedUiThings[it.id])
        results.addAll(result)
      }

      if(shouldResetCodeVision) {
        editor.lensContextOrThrow.discardPending()
        return@executeOnPooledThread
      }

      if (!inTestSyncMode) {
        application.invokeLater {
          calcLifetime.executeIfAlive { consumer(results) }
        }
      }
      else {
        consumer(results)
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

      lifetime.onTermination {
        if (indicator.isRunning) indicator.cancel()
      }
    }
    else {
      runnable()
    }

    return indicator
  }


  @TestOnly
  fun calculateCodeVisionSync(editor: Editor, testRootDisposable: Disposable) {
    calculateFrontendLenses(testRootDisposable.createLifetime(), editor, singleLensProvider = null, inTestSyncMode = true) {
      editor.lensContextOrThrow.setResults(it)
    }
  }

}