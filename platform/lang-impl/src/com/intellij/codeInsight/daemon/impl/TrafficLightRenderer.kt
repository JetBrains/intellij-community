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
import com.intellij.lang.Language
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.*
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
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.ArrayUtilRt
import com.intellij.util.SlowOperations
import com.intellij.util.UtilBundle.message
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.storage.HeavyProcessLatch
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.GridBag
import groovy.transform.Internal
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntBinaryOperator
import it.unimi.dsi.fastutil.objects.Object2IntMaps
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Container
import javax.swing.JComponent

open class TrafficLightRenderer private constructor(
  project: Project,
  document: Document,
  editor: Editor?
) : ErrorStripeRenderer, Disposable {
  protected val project: Project
  private val document: Document
  private val daemonCodeAnalyzer: DaemonCodeAnalyzerImpl
  val severityRegistrar: SeverityRegistrar
  private val errorCount = Object2IntMaps.synchronize(Object2IntOpenHashMap<HighlightSeverity>())
  protected val uIController: UIController
  // true if getPsiFile() is in library sources
  private val inLibrary: Boolean
  private val shouldHighlight: Boolean
  private var cachedErrors: IntArray = ArrayUtilRt.EMPTY_INT_ARRAY
  // each root language -> its highlighting level
  private val fileHighlightingSettings: MutableMap<Language, FileHighlightingSetting>

  @Volatile
  private var highlightingSettingsModificationCount: Long

  constructor(project: Project, document: Document) : this(project = project, document = document, editor = null)

  @Internal
  constructor(project: Project, editor: Editor) : this(project = project, document = editor.getDocument(), editor = editor)

  init {
    // to be able to find PsiFile without "slow op in EDT" exceptions
    ThreadingAssertions.assertBackgroundThread()

    this.project = project
    daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl
    this.document = document
    this.severityRegistrar = SeverityRegistrar.getSeverityRegistrar(this.project)

    init(project, document)
    uIController = if (editor == null) createUIController() else createUIController(editor)
    data class Stuff(
      @JvmField val fileHighlightingSettings: MutableMap<Language, FileHighlightingSetting>,
      @JvmField val inLibrary: Boolean,
      @JvmField val shouldHighlight: Boolean,
    )

    val info = ReadAction.compute(ThrowableComputable {
      val psiFile = psiFile ?: return@ThrowableComputable Stuff(
        fileHighlightingSettings = mutableMapOf<Language, FileHighlightingSetting>(),
        inLibrary = false,
        shouldHighlight = false,
      )

      val viewProvider = psiFile.getViewProvider()
      val languages = viewProvider.getLanguages()
      val settingMap = HashMap<Language, FileHighlightingSetting>(languages.size)
      val settings = HighlightingSettingsPerFile.getInstance(project)
      for (psiRoot in viewProvider.getAllFiles()) {
        val setting = settings.getHighlightingSettingForRoot(psiRoot)
        settingMap.put(psiRoot.getLanguage(), setting)
      }

      val fileIndex = ProjectRootManager.getInstance(project).getFileIndex()
      val virtualFile = psiFile.getVirtualFile()!!
      val inLib = fileIndex.isInLibrary(virtualFile) && !fileIndex.isInContent(virtualFile)
      val shouldHighlight = ProblemHighlightFilter.shouldHighlightFile(psiFile)
      Stuff(fileHighlightingSettings = settingMap, inLibrary = inLib, shouldHighlight = shouldHighlight)
    })
    fileHighlightingSettings = info.fileHighlightingSettings
    inLibrary = info.inLibrary
    shouldHighlight = info.shouldHighlight
    highlightingSettingsModificationCount = HighlightingSettingsPerFile.getInstance(project).getModificationCount()
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
    EdtInvocationManager.invokeLaterIfNeeded(Runnable {
      for (rangeHighlighter in model.getAllHighlighters()) {
        incErrorCount(rangeHighlighter, 1)
      }
    })
  }

  private val psiFile: PsiFile?
    get() = PsiDocumentManager.getInstance(this.project).getPsiFile(document)

  open val errorCounts: IntArray
    /**
     * Returns a new instance of an array filled with a number of highlighters with a given severity.
     * `errorCount[idx]` equals to a number of highlighters of severity with index `idx` in this markup model.
     * Severity index can be obtained via [SeverityRegistrar.getSeverityIdx].
     */
    get() = cachedErrors.clone()

  open fun refresh(editorMarkupModel: EditorMarkupModelImpl?) {
    val severities = severityRegistrar.allSeverities
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
    val info = HighlightInfo.fromRangeHighlighter(highlighter) ?: return
    val infoSeverity = info.severity
    if (infoSeverity.myVal <= HighlightSeverity.TEXT_ATTRIBUTES.myVal) {
      return
    }

    errorCount.mergeInt(infoSeverity, delta, IntBinaryOperator { a, b -> Integer.sum(a, b) })
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
      return settings.getModificationCount() == highlightingSettingsModificationCount
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
    if (!daemonCodeAnalyzer.isHighlightingAvailable(psiFile)) {
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

    for (entry in fileHighlightingSettings.entries) {
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
      daemonCodeAnalyzer.getPassesToShowProgressFor(document),
      Condition { p: ProgressableTextEditorHighlightingPass? ->
        !StringUtil.isEmpty(
          p!!.getPresentableName()) && p.getProgress() >= 0
      })

    status.errorAnalyzingFinished = daemonCodeAnalyzer.isAllAnalysisFinished(psiFile)
    if (!daemonCodeAnalyzer.isUpdateByTimerEnabled()) {
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
      else if (fileHighlightingSettings.containsValue(FileHighlightingSetting.ESSENTIAL)) {
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

    override fun getHighlightLevels(): List<LanguageHighlightLevel> {
      return fileHighlightingSettings.entries.map { entry ->
        LanguageHighlightLevel(langID = entry.key!!.id, level = FileHighlightingSetting.toInspectionsLevel(entry.value!!))
      }
    }

    override fun setHighLightLevel(level: LanguageHighlightLevel) {
      val psiFile = psiFile
      if (psiFile != null && !project.isDisposed() && !highlightLevels.contains(level)) {
        val viewProvider = psiFile.getViewProvider()

        val language = Language.findLanguageByID(level.langID)
        if (language != null) {
          val root = viewProvider.getPsi(language) ?: return
          val setting = FileHighlightingSetting.fromInspectionsLevel(level.level)
          HighlightLevelUtil.forceRootHighlighting(root, setting)
          InjectedLanguageManager.getInstance(project).dropFileCaches(psiFile)
          daemonCodeAnalyzer.restart()
          // after that, TrafficLightRenderer will be recreated anew, no need to patch myFileHighlightingSettings
        }
      }
    }

    override fun fillHectorPanels(container: Container, gc: GridBag) {
      val psiFile = psiFile ?: return
      val list = ArrayList<HectorComponentPanel>()
      for (hp in HectorComponentPanelsProvider.EP_NAME.getExtensionList(project)) {
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

    override fun canClosePopup(): Boolean {
      if (additionalPanels.isEmpty()) {
        return true
      }
      if (additionalPanels.all { it.canClose() }) {
        val psiFile = psiFile
        var hasModified = false
        for (panel in additionalPanels.asSequence().filter { it.isModified() }) {
          hasModified = true
          applyPanel(panel)
        }
        if (hasModified) {
          if (psiFile != null) {
            InjectedLanguageManager.getInstance(project).dropFileCaches(psiFile)
          }
          daemonCodeAnalyzer.restart()
        }
        return true
      }
      return false
    }

    override fun onClosePopup() {
      additionalPanels.forEach { it.disposeUIResources() }
      additionalPanels = mutableListOf<HectorComponentPanel>()
    }

    override fun toggleProblemsView() {
      val file = psiFile
      val virtualFile = file?.getVirtualFile()
      val document = file?.getViewProvider()?.getDocument()
      ProblemsView.toggleCurrentFileProblems(project, virtualFile, document)
    }
  }

  protected open inner class DefaultUIController : AbstractUIController() {
    // only create actions when daemon widget used
    @Suppress("RemoveRedundantQualifierName")
    private val menuActions by lazy {
      java.util.List.of(
        ActionManager.getInstance().getAction("ConfigureInspectionsAction"),
        DaemonEditorPopup.createGotoGroup(),
        Separator.create(),
        ShowImportTooltipAction(this@TrafficLightRenderer),
      )
    }

    override fun getActions(): List<AnAction> = menuActions

    override fun isToolbarEnabled(): Boolean = true
  }

  // actions shouldn't be anonymous classes for statistics reasons
  private class ShowImportTooltipAction(private val renderer: TrafficLightRenderer)
    : ToggleAction(EditorBundle.message("iw.show.import.tooltip")) {
    override fun isSelected(e: AnActionEvent): Boolean {
      val psiFile = renderer.psiFile
      return psiFile != null && renderer.daemonCodeAnalyzer.isImportHintsEnabled(psiFile)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      renderer.daemonCodeAnalyzer.setImportHintsEnabled(renderer.psiFile ?: return, state)
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      val psiFile = renderer.psiFile
      e.presentation.setEnabled(psiFile != null && renderer.daemonCodeAnalyzer.isAutohintsAvailable(psiFile))
    }

    override fun isDumbAware(): Boolean = true
  }

  fun invalidate() {
    highlightingSettingsModificationCount = -1
  }
}

private fun toPercent(progress: Double, finished: Boolean): Int {
  val percent = (progress * 100).toInt()
  return if (percent == 100 && !finished) 99 else percent
}

private fun applyPanel(panel: HectorComponentPanel) {
  try {
    panel.apply()
  }
  catch (_: ConfigurationException) {
  }
}