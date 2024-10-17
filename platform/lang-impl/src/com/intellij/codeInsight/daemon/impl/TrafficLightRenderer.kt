// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.codeInsight.daemon.impl

import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.codeInsight.daemon.DaemonBundle
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.icons.AllIcons
import com.intellij.ide.PowerSaveMode
import com.intellij.ide.impl.executeOnPooledThread
import com.intellij.lang.Language
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ArrayUtilRt
import com.intellij.util.Function
import com.intellij.util.SlowOperations
import com.intellij.util.UtilBundle.message
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.storage.HeavyProcessLatch
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.UIUtil
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntBinaryOperator
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntMaps
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Container
import java.util.*
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.JComponent

open class TrafficLightRenderer private constructor(
  project: Project,
  document: Document,
  editor: Editor?
) : ErrorStripeRenderer, Disposable {
  protected val project: Project
  private val myDocument: Document
  private val myDaemonCodeAnalyzer: DaemonCodeAnalyzerImpl
  val severityRegistrar: SeverityRegistrar
  private val errorCount: Object2IntMap<HighlightSeverity?> = Object2IntMaps.synchronize<HighlightSeverity?>(
    Object2IntOpenHashMap<HighlightSeverity?>())
  protected val uIController: UIController
  private val inLibrary: Boolean // true if getPsiFile() is in library sources
  private val shouldHighlight: Boolean
  private var cachedErrors: IntArray = ArrayUtilRt.EMPTY_INT_ARRAY
  private val myFileHighlightingSettings: MutableMap<Language?, FileHighlightingSetting?> // each root language -> its highlighting level

  @Volatile
  private var myHighlightingSettingsModificationCount: Long

  constructor(project: Project, document: Document) : this(project, document, null)
  protected constructor(project: Project, editor: Editor) : this(project, editor.getDocument(), editor)

  init {
    ApplicationManager.getApplication().assertIsNonDispatchThread() // to be able to find PsiFile without "slow op in EDT" exceptions
    this.project = project
    myDaemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl
    myDocument = document
    this.severityRegistrar = SeverityRegistrar.getSeverityRegistrar(this.project)

    init(project, myDocument)
    this.uIController = if (editor == null) createUIController() else createUIController(editor)
    data class Stuff(
      val fileHighlightingSettings: MutableMap<Language?, FileHighlightingSetting?>,
      val inLibrary: Boolean,
      val shouldHighlight: Boolean
    )

    val info = ReadAction.compute<Stuff, RuntimeException?>(ThrowableComputable {
      val psiFile = this.psiFile
      if (psiFile == null) {
        return@compute Stuff(mutableMapOf<Language?, FileHighlightingSetting?>(), false, false)
      }
      val viewProvider = psiFile.getViewProvider()
      val languages = viewProvider.getLanguages()
      val settingMap: MutableMap<Language?, FileHighlightingSetting?> = HashMap<Language?, FileHighlightingSetting?>(languages.size)
      val settings = HighlightingSettingsPerFile.getInstance(project)
      for (psiRoot in viewProvider.getAllFiles()) {
        val setting = settings.getHighlightingSettingForRoot(psiRoot)
        settingMap.put(psiRoot.getLanguage(), setting)
      }

      val fileIndex = ProjectRootManager.getInstance(project).getFileIndex()
      val virtualFile = checkNotNull(psiFile.getVirtualFile())
      val inLib = fileIndex.isInLibrary(virtualFile) && !fileIndex.isInContent(virtualFile)
      val shouldHighlight = ProblemHighlightFilter.shouldHighlightFile(this.psiFile)
      Stuff(settingMap, inLib, shouldHighlight)
    })
    myFileHighlightingSettings = info.fileHighlightingSettings
    inLibrary = info.inLibrary
    shouldHighlight = info.shouldHighlight
    myHighlightingSettingsModificationCount = HighlightingSettingsPerFile.getInstance(project).getModificationCount()
  }

  private fun init(project: Project, document: Document) {
    refresh(null)

    val model = DocumentMarkupModel.forDocument(document, project, true) as MarkupModelEx
    model.addMarkupModelListener(this, object : MarkupModelListener {
      override fun afterAdded(highlighter: RangeHighlighterEx) {
        incErrorCount(highlighter, 1)
      }

      override fun afterRemoved(highlighter: RangeHighlighterEx) {
        incErrorCount(highlighter, -1)
      }
    })
    UIUtil.invokeLaterIfNeeded(Runnable {
      for (rangeHighlighter in model.getAllHighlighters()) {
        incErrorCount(rangeHighlighter, 1)
      }
    })
  }

  private val psiFile: PsiFile?
    get() = PsiDocumentManager.getInstance(this.project).getPsiFile(myDocument)

  open val errorCounts: IntArray
    /**
     * Returns a new instance of an array filled with a number of highlighters with a given severity.
     * `errorCount[idx]` equals to a number of highlighters of severity with index `idx` in this markup model.
     * Severity index can be obtained via [SeverityRegistrar.getSeverityIdx].
     */
    get() = cachedErrors.clone()

  protected open fun refresh(editorMarkupModel: EditorMarkupModelImpl?) {
    val severities = severityRegistrar.getAllSeverities()
    if (cachedErrors.size != severities.size) {
      cachedErrors = IntArray(severities.size)
    }

    for (severity in severities) {
      val severityIndex = severityRegistrar.getSeverityIdx(severity)
      cachedErrors[severityIndex] = errorCount.getInt(severity)
    }
  }

  override fun dispose() {
    errorCount.clear()
    cachedErrors = ArrayUtilRt.EMPTY_INT_ARRAY
  }

  private fun incErrorCount(highlighter: RangeHighlighter, delta: Int) {
    val info = HighlightInfo.fromRangeHighlighter(highlighter)
    if (info == null) return
    val infoSeverity = info.getSeverity()
    if (infoSeverity.myVal <= HighlightSeverity.TEXT_ATTRIBUTES.myVal) return

    errorCount.mergeInt(infoSeverity, delta, IntBinaryOperator { a: Int, b: Int -> Integer.sum(a, b) })
  }

  open val isValid: Boolean
    /**
     * when highlighting level changed, re-create TrafficLightRenderer (and recompute levels in its ctr)
     * @see ErrorStripeUpdateManager.setOrRefreshErrorStripeRenderer
     */
    get() {
      val psiFile: PsiFile?
      SlowOperations.knownIssue("IDEA-301732, EA-829415").use { ignore ->
        psiFile = this.psiFile
        if (psiFile == null) return false
      }
      val settings = HighlightingSettingsPerFile.getInstance(psiFile!!.getProject())
      return settings.getModificationCount() == myHighlightingSettingsModificationCount
    }

  @ApiStatus.Internal
  class DaemonCodeAnalyzerStatus internal constructor() {
    @JvmField
    // all passes are done
    var errorAnalyzingFinished: Boolean = false

    var passes: MutableList<ProgressableTextEditorHighlightingPass> = mutableListOf()
    var errorCounts: IntArray = ArrayUtilRt.EMPTY_INT_ARRAY
    var reasonWhyDisabled: @Nls String? = null
    var reasonWhySuspended: @Nls String? = null

    var heavyProcessType: HeavyProcessLatch.Type? = null
    // by default, full inspect mode is expected
    internal var minimumLevel = FileHighlightingSetting.FORCE_HIGHLIGHTING

    override fun toString(): String {
      val s = StringBuilder(("DS: finished=" + errorAnalyzingFinished
                             + "; pass statuses: " + passes.size + "; "))
      for (passStatus in passes) {
        s.append(
          String.format("(%s %2.0f%% %b)", passStatus.getPresentableName(), passStatus.getProgress() * 100, passStatus.isFinished()))
      }
      s.append("; error counts: ").append(errorCounts.size).append(": ").append(IntArrayList(errorCounts))
      if (reasonWhyDisabled != null) {
        s.append("; reasonWhyDisabled=").append(reasonWhyDisabled)
      }
      if (reasonWhySuspended != null) {
        s.append("; reasonWhySuspended").append(reasonWhySuspended)
      }
      return s.toString()
    }
  }

  @get:ApiStatus.Internal
  val daemonCodeAnalyzerStatus: DaemonCodeAnalyzerStatus
    get() {
      ApplicationManager.getApplication().assertIsNonDispatchThread()
      ApplicationManager.getApplication().assertReadAccessAllowed()
      return getDaemonCodeAnalyzerStatus(this.severityRegistrar)
    }

  protected open fun getDaemonCodeAnalyzerStatus(severityRegistrar: SeverityRegistrar): DaemonCodeAnalyzerStatus {
    // this method is rather expensive and PSI-related, need to execute in BGT and cache the result to show in EDT later
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val status = DaemonCodeAnalyzerStatus()
    status.errorAnalyzingFinished = true
    val psiFile = this.psiFile
    if (psiFile == null) {
      status.reasonWhyDisabled = DaemonBundle.message("process.title.no.file")
      return status
    }
    if (project.isDisposed()) {
      status.reasonWhyDisabled = DaemonBundle.message("process.title.project.is.disposed")
      return status
    }
    if (!myDaemonCodeAnalyzer.isHighlightingAvailable(psiFile)) {
      if (!psiFile.isPhysical()) {
        status.reasonWhyDisabled = DaemonBundle.message("process.title.file.is.generated")
        return status
      }
      if (psiFile is PsiCompiledElement) {
        status.reasonWhyDisabled = DaemonBundle.message("process.title.file.is.decompiled")
        return status
      }
      val fileType = psiFile.getFileType()
      if (fileType.isBinary()) {
        status.reasonWhyDisabled = DaemonBundle.message("process.title.file.is.binary")
        return status
      }
      status.reasonWhyDisabled = DaemonBundle.message("process.title.highlighting.is.disabled.for.this.file")
      return status
    }

    val provider = psiFile.getViewProvider()
    val languages = provider.getLanguages()
    var shouldHighlight = languages.isEmpty()

    for (entry in myFileHighlightingSettings.entries) {
      val level: FileHighlightingSetting = entry.value!!
      shouldHighlight = shouldHighlight or (level != FileHighlightingSetting.SKIP_HIGHLIGHTING)
      status.minimumLevel = if (status.minimumLevel.compareTo(level) < 0) status.minimumLevel else level
    }
    shouldHighlight = shouldHighlight and this.shouldHighlight

    if (!shouldHighlight) {
      status.reasonWhyDisabled = DaemonBundle.message("process.title.highlighting.level.is.none")
      return status
    }

    val heavyOperation = HeavyProcessLatch.INSTANCE.findRunningExcept(HeavyProcessLatch.Type.Syncing)
    if (heavyOperation != null) {
      status.reasonWhySuspended = heavyOperation.getDisplayName()
      status.heavyProcessType = heavyOperation.getType()
      return status
    }

    status.errorCounts = this.errorCounts
    status.passes = ContainerUtil.filter<ProgressableTextEditorHighlightingPass?>(
      myDaemonCodeAnalyzer.getPassesToShowProgressFor(myDocument),
      Condition { p: ProgressableTextEditorHighlightingPass? ->
        !StringUtil.isEmpty(
          p!!.getPresentableName()) && p.getProgress() >= 0
      })

    status.errorAnalyzingFinished = myDaemonCodeAnalyzer.isAllAnalysisFinished(psiFile)
    if (!myDaemonCodeAnalyzer.isUpdateByTimerEnabled()) {
      status.reasonWhySuspended = DaemonBundle.message("process.title.highlighting.is.paused.temporarily")
    }
    fillDaemonCodeAnalyzerErrorsStatus(status, severityRegistrar)

    return status
  }

  protected open fun fillDaemonCodeAnalyzerErrorsStatus(status: DaemonCodeAnalyzerStatus, severityRegistrar: SeverityRegistrar) {
  }

  override fun getStatus(): AnalyzerStatus {
    // this method is rather expensive and PSI-related, need to execute in BGT and cache the result to show in EDT later
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    ApplicationManager.getApplication().assertReadAccessAllowed()
    if (PowerSaveMode.isEnabled()) {
      return AnalyzerStatus(AllIcons.General.InspectionsPowerSaveMode,
                            InspectionsBundle.message("code.analysis.is.disabled.in.power.save.mode"),
                            "",
                            this.uIController).withState(InspectionsState.DISABLED)
    }
    val status = getDaemonCodeAnalyzerStatus(this.severityRegistrar)

    val title: String?
    val details: String?
    val state: InspectionsState?
    val isDumb = isDumb(this.project)

    val statusItems: MutableList<SeverityStatusItem> = ArrayList<SeverityStatusItem>()
    val errorCounts = status.errorCounts
    for (i in errorCounts.indices.reversed()) {
      val count = errorCounts[i]
      if (count > 0) {
        val severity = severityRegistrar.getSeverityByIndex(i)
        if (severity != null) {
          val icon = severityRegistrar.getRendererIconBySeverity(severity,
                                                                 status.minimumLevel == FileHighlightingSetting.FORCE_HIGHLIGHTING)
          var next: SeverityStatusItem? = SeverityStatusItem(severity, icon, count, severity.getCountMessage(count))
          while (!statusItems.isEmpty()) {
            val merged = StatusItemMerger.runMerge(ContainerUtil.getLastItem<SeverityStatusItem?>(statusItems), next!!)
            if (merged == null) break

            statusItems.removeAt(statusItems.size - 1)
            next = merged
          }
          statusItems.add(next!!)
        }
      }
    }

    if (status.errorAnalyzingFinished) {
      if (isDumb) {
        title = DaemonBundle.message("shallow.analysis.completed")
        details = DaemonBundle.message("shallow.analysis.completed.details")
        state = InspectionsState.SHALLOW_ANALYSIS_COMPLETE
      }
      else if (myFileHighlightingSettings.containsValue(FileHighlightingSetting.ESSENTIAL)) {
        title = DaemonBundle.message("essential.analysis.completed")
        details = DaemonBundle.message("essential.analysis.completed.details")
        state = InspectionsState.ESSENTIAL_ANALYSIS_COMPLETE
      }
      else {
        title = if (statusItems.isEmpty()) DaemonBundle.message("no.errors.or.warnings.found") else ""
        details = ""
        state = InspectionsState.NO_PROBLEMS_FOUND
      }
    }
    else {
      title = DaemonBundle.message("performing.code.analysis")
      details = ""
      state = InspectionsState.PERFORMING_CODE_ANALYSIS
    }

    if (!statusItems.isEmpty()) {
      val result = AnalyzerStatus(
        icon = statusItems[0].icon,
        title = title,
        details = "",
        controller = this.uIController,
      )
        .withNavigation(true)
        .withState(state)
        .withExpandedStatus(statusItems.map {
          val metadata = TrafficLightStatusItemMetadata(it.problemCount, it.severity)
          StatusItem(text = it.problemCount.toString(), icon = it.icon, detailsText = it.countMessage, metadata = metadata)
        })

      return if (status.errorAnalyzingFinished) {
        result
      }
      else {
        result.withAnalyzingType(AnalyzingType.PARTIAL)
          .withPasses(status.passes.map {
            PassWrapper(presentableName = it.presentableName!!, percent = toPercent(it.getProgress(), it.isFinished))
          })
      }
    }
    if (StringUtil.isNotEmpty(status.reasonWhyDisabled)) {
      return AnalyzerStatus(AllIcons.General.InspectionsTrafficOff,
                            DaemonBundle.message("no.analysis.performed"),
                            status.reasonWhyDisabled!!,
                            this.uIController).withTextStatus(DaemonBundle.message("iw.status.off")).withState(InspectionsState.OFF)
    }
    if (StringUtil.isNotEmpty(status.reasonWhySuspended)) {
      return AnalyzerStatus(
        icon = AllIcons.General.InspectionsPause,
        title = DaemonBundle.message("analysis.suspended"),
        details = status.reasonWhySuspended!!, controller = this.uIController,
      )
        .withState(InspectionsState.PAUSED)
        .withTextStatus(if (status.heavyProcessType != null) status.heavyProcessType.toString() else DaemonBundle.message("iw.status.paused"))
        .withAnalyzingType(AnalyzingType.SUSPENDED)
    }
    if (status.errorAnalyzingFinished) {
      val inspectionsCompletedIcon = if (status.minimumLevel == FileHighlightingSetting.FORCE_HIGHLIGHTING) {
        AllIcons.General.InspectionsOK
      }
      else {
        AllIcons.General.InspectionsOKEmpty
      }
      return if (isDumb) {
        AnalyzerStatus(icon = AllIcons.General.InspectionsPause, title = title, details = details, controller = this.uIController)
          .withTextStatus(message("heavyProcess.type.indexing"))
          .withState(InspectionsState.INDEXING)
          .withAnalyzingType(AnalyzingType.SUSPENDED)
      }
      else {
        AnalyzerStatus(icon = inspectionsCompletedIcon, title = title, details = details, controller = uIController)
      }
    }

    return AnalyzerStatus(
      icon = AllIcons.General.InspectionsEye,
      title = DaemonBundle.message("no.errors.or.warnings.found"),
      details = details,
      controller = this.uIController,
    )
      .withTextStatus(DaemonBundle.message("iw.status.analyzing"))
      .withState(InspectionsState.ANALYZING)
      .withAnalyzingType(AnalyzingType.EMPTY)
      .withPasses(status.passes.map {
        PassWrapper(presentableName = it.presentableName!!, percent = toPercent(it.getProgress(), it.isFinished))
      })
  }

  protected open fun createUIController(): UIController {
    // to assert no slow ops in EDT
    ThreadingAssertions.assertBackgroundThread()
    return AbstractUIController()
  }

  protected fun createUIController(editor: Editor): UIController {
    // to assert no slow ops in EDT
    ThreadingAssertions.assertBackgroundThread()
    val mergeEditor = editor.getUserData(DiffUserDataKeys.MERGE_EDITOR_FLAG) == true
    return if (editor.getEditorKind() == EditorKind.DIFF && !mergeEditor) AbstractUIController() else DefaultUIController()
  }

  protected open inner class AbstractUIController internal constructor() : UIController {
    private var additionalPanels = mutableListOf<HectorComponentPanel>()

    init {
      ThreadingAssertions.assertBackgroundThread()
    }

    @Suppress("RemoveRedundantQualifierName")
    override fun getAvailableLevels(): List<InspectionsLevel?> {
      return when {
        inLibrary -> java.util.List.of(InspectionsLevel.NONE, InspectionsLevel.SYNTAX)
        ApplicationManager.getApplication().isInternal() -> {
          java.util.List.of(InspectionsLevel.NONE, InspectionsLevel.SYNTAX, InspectionsLevel.ESSENTIAL, InspectionsLevel.ALL)
        }
        else -> java.util.List.of(InspectionsLevel.NONE, InspectionsLevel.SYNTAX, InspectionsLevel.ALL)
      }
    }

    override fun getHighlightLevels(): MutableList<LanguageHighlightLevel?> {
      return ContainerUtil.map<MutableMap.MutableEntry<Language?, FileHighlightingSetting?>?, LanguageHighlightLevel?>(
        myFileHighlightingSettings.entries,
        Function { entry: MutableMap.MutableEntry<Language?, FileHighlightingSetting?>? ->
          LanguageHighlightLevel(
            entry!!.key!!.getID(), FileHighlightingSetting.toInspectionsLevel(
              entry.value!!))
        })
    }

    override fun setHighLightLevel(level: LanguageHighlightLevel) {
      val psiFile: PsiFile? = this.psiFile
      if (psiFile != null && !this.project.isDisposed() && !getHighlightLevels().contains(level)) {
        val viewProvider = psiFile.getViewProvider()

        val language = Language.findLanguageByID(level.langID)
        if (language != null) {
          val root: PsiElement? = viewProvider.getPsi(language)
          if (root == null) return
          val setting = FileHighlightingSetting.fromInspectionsLevel(level.level)
          HighlightLevelUtil.forceRootHighlighting(root, setting)
          InjectedLanguageManager.getInstance(this.project).dropFileCaches(psiFile)
          myDaemonCodeAnalyzer.restart()
          // after that TrafficLightRenderer will be recreated anew, no need to patch myFileHighlightingSettings
        }
      }
    }

    override fun fillHectorPanels(container: Container, gc: GridBag) {
      val psiFile: PsiFile? = this.psiFile
      if (psiFile != null) {
        val list: MutableList<HectorComponentPanel> = ArrayList<HectorComponentPanel>()
        for (hp in HectorComponentPanelsProvider.EP_NAME.getExtensionList(this.project)) {
          val configurable = hp.createConfigurable(psiFile)
          if (configurable != null) {
            list.add(configurable)
          }
        }
        additionalPanels = list

        for (panel in additionalPanels) {
          val c: JComponent?
          try {
            panel.reset()
            c = panel.createComponent()
          }
          catch (e: ProcessCanceledException) {
            throw e
          }
          catch (e: Throwable) {
            Logger.getInstance(TrafficLightRenderer::class.java).error(e)
            continue
          }

          if (c != null) {
            container.add(c, gc.nextLine().next().fillCellHorizontally().coverLine().weightx(1.0))
          }
        }
      }
    }

    override fun canClosePopup(): Boolean {
      if (additionalPanels.isEmpty()) {
        return true
      }
      if (ContainerUtil.all<HectorComponentPanel?>(additionalPanels, Condition { p: HectorComponentPanel? -> p!!.canClose() })) {
        val psiFile: PsiFile? = this.psiFile
        if (additionalPanels.stream().filter { p: HectorComponentPanel? -> p!!.isModified() }.peek { panel: HectorComponentPanel? ->
            Companion.applyPanel(
              panel!!)
          }.count() > 0) {
          if (psiFile != null) {
            InjectedLanguageManager.getInstance(this.project).dropFileCaches(psiFile)
          }
          myDaemonCodeAnalyzer.restart()
        }
        return true
      }
      return false
    }

    override fun onClosePopup() {
      additionalPanels.forEach(Consumer { p: HectorComponentPanel? -> p!!.disposeUIResources() })
      additionalPanels = mutableListOf<HectorComponentPanel?>()
    }

    override fun toggleProblemsView() {
      val file: PsiFile? = this.psiFile
      val virtualFile = if (file == null) null else file.getVirtualFile()
      val document = if (file == null) null else file.getViewProvider().getDocument()
      ProblemsView.toggleCurrentFileProblems(this.project, virtualFile, document)
    }
  }

  protected open inner class DefaultUIController : AbstractUIController() {
    // only create actions when daemon widget used
    private val menuActions: Supplier<MutableList<AnAction?>?> = SynchronizedClearableLazy<MutableList<AnAction?>?> {
      val result: MutableList<AnAction?> = ArrayList<AnAction?>()
      result.add(ActionManager.getInstance().getAction("ConfigureInspectionsAction"))
      result.add(DaemonEditorPopup.createGotoGroup())
      result.add(Separator.create())
      result.add(ShowImportTooltipAction(this@TrafficLightRenderer))
      result
    }

    override fun getActions(): MutableList<AnAction?> {
      return menuActions.get()!!
    }

    override fun isToolbarEnabled(): Boolean {
      return true
    }
  }

  // Actions shouldn't be anonymous classes for statistics reasons.
  private class ShowImportTooltipAction(private val myRenderer: TrafficLightRenderer) : ToggleAction(
    EditorBundle.message("iw.show.import.tooltip")) {
    override fun isSelected(e: AnActionEvent): Boolean {
      val psiFile = myRenderer.psiFile
      return psiFile != null && myRenderer.myDaemonCodeAnalyzer.isImportHintsEnabled(psiFile)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val psiFile = myRenderer.psiFile
      if (psiFile != null) {
        myRenderer.myDaemonCodeAnalyzer.setImportHintsEnabled(psiFile, state)
      }
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      val psiFile = myRenderer.psiFile
      e.getPresentation().setEnabled(psiFile != null && myRenderer.myDaemonCodeAnalyzer.isAutohintsAvailable(psiFile))
    }

    override fun isDumbAware(): Boolean {
      return true
    }
  }

  fun invalidate() {
    myHighlightingSettingsModificationCount = -1
  }

  companion object {
    private val LOG = Logger.getInstance(TrafficLightRenderer::class.java)

    /**
     * Prefer using [TrafficLightRendererContributor] instead
     */
    fun setTrafficLightOnEditor(
      project: Project,
      editorMarkupModel: EditorMarkupModel,
      modalityState: ModalityState,
      createTrafficRenderer: Supplier<out TrafficLightRenderer?>
    ) {
      project.executeOnPooledThread(Runnable {
        val tlRenderer: TrafficLightRenderer? = createTrafficRenderer.get()
        if (tlRenderer == null) return@executeOnPooledThread
        ApplicationManager.getApplication().invokeLater(Runnable {
          val editor = editorMarkupModel.getEditor()
          if (project.isDisposed() || editor.isDisposed()) {
            LOG.debug("Traffic light won't be set to editor: project dispose ", project.isDisposed(),
                      " , editor dispose " + editor.isDisposed())
            Disposer.dispose(tlRenderer) // would be registered in setErrorStripeRenderer() below
            return@invokeLater
          }
          editorMarkupModel.setErrorStripeRenderer(tlRenderer)
        }, modalityState)
      })
    }

    private fun toPercent(progress: Double, finished: Boolean): Int {
      val percent = (progress * 100).toInt()
      return if (percent == 100 && !finished) 99 else percent
    }


    private fun applyPanel(panel: HectorComponentPanel) {
      try {
        panel.apply()
      }
      catch (ignored: ConfigurationException) {
      }
    }
  }
}
