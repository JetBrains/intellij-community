// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.LineMarkersPass
import com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.InspectionToolProvider
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.lang.ExternalAnnotatorsFilter
import com.intellij.lang.LanguageAnnotators
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.search.IndexPatternBuilder
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.xml.XmlFileNSInfoProvider
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.FileTreeAccessFilter
import com.intellij.testFramework.HighlightTestInfo
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.StartupActivityTestUtil
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.configureInspections
import com.intellij.testFramework.enableInspectionTool
import com.intellij.testFramework.enableInspectionTools
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.xml.XmlSchemaProvider
import org.jetbrains.annotations.TestOnly
import java.util.function.Function
import java.util.function.Supplier

/**
 * A [HeavyJavaCodeInsightFixture] for a highlighting test, as [DaemonAnalyzerTestCase] gives it.
 *
 * The fixture configures [localInspectionTools] in the inspection profile of the test and prepares the daemon for the test.
 * The highlighting runs with the tree access filter: it fails when it loads the tree of a file that no editor of the test shows.
 *
 * Create it with [daemonAnalyzerFixture].
 */
@TestOnly
open class DaemonAnalyzerFixture(
  context: Context,
  testDataPath: String,
  private val localInspectionTools: Array<LocalInspectionTool>,
) : HeavyJavaCodeInsightFixture(context, testDataPath) {
  /**
   * When `true`, the highlighting can change the document. The test method or its class sets it with [CanChangeDocumentDuringHighlighting].
   */
  var isCanChangeDocumentDuringHighlighting: Boolean = annotatedWith(CanChangeDocumentDuringHighlighting::class.java)

  /**
   * When `true`, [doHighlighting] also runs the line marker passes. It is `doTestLineMarkers()` of [DaemonAnalyzerTestCase].
   */
  var isTestLineMarkers: Boolean = false

  /**
   * When `false`, [doHighlighting] skips the external tools pass. It is `doExternalValidation()` of [DaemonAnalyzerTestCase].
   */
  var isExternalValidation: Boolean = true

  /**
   * When `true`, [doHighlighting] runs only the external tools pass and the passes that it needs.
   * It is `forceExternalValidation()` of [DaemonAnalyzerTestCase].
   */
  var isForceExternalValidation: Boolean = false

  /**
   * The daemon of [project].
   */
  val daemonCodeAnalyzer: DaemonCodeAnalyzerImpl
    get() = DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl

  /**
   * The test helper of the daemon of [project]. It is one instance for the test, so a setting on it, for example
   * `mustWaitForSmartModeByDefault`, stays for the later calls, as `myTestDaemonCodeAnalyzer` of [DaemonAnalyzerTestCase] did.
   */
  val testDaemonCodeAnalyzer: TestDaemonCodeAnalyzerImpl by lazy { TestDaemonCodeAnalyzerImpl(project) }

  private val virtualFileFilter = FileTreeAccessFilter()

  override fun setUp() {
    super.setUp()
    runInEdtAndWait {
      configureInspections(localInspectionTools.map { it as InspectionProfileEntry }.toTypedArray(), project, testRootDisposable)
      TestDaemonCodeAnalyzerImpl(project).prepareForTest()
      DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled = false
    }
    if (isStressTest) {
      preloadClassesForStressTest()
    }
  }

  private fun preloadClassesForStressTest() {
    IntentionManager.getInstance().availableIntentions // hack to avoid slowdowns in PyExtensionFactory
    PathManagerEx.guessTestDataLookupStrategy() // to cache stuff; the ultimate repository has no shared testData root, so do not resolve the path itself
    ReferenceProvidersRegistry.getInstance() // pre-load tons of classes
    InjectedLanguageManager.getInstance(project) // zillion of Dom Sem classes
    LanguageAnnotators.INSTANCE.allForLanguage(JavaLanguage.INSTANCE) // pile of annotator classes loads
    LanguageAnnotators.INSTANCE.allForLanguage(XMLLanguage.INSTANCE)
    ProblemHighlightFilter.EP_NAME.extensions
    ImplicitUsageProvider.EP_NAME.extensionList
    XmlSchemaProvider.EP_NAME.extensionList
    XmlFileNSInfoProvider.EP_NAME.extensionList
    ExternalAnnotatorsFilter.EXTENSION_POINT_NAME.extensionList
    IndexPatternBuilder.EP_NAME.extensionList
  }

  override fun tearDown() {
    runAll(
      {
        ApplicationManager.getApplication().getServiceIfCreated(DaemonCodeAnalyzerSettings::class.java)?.isImportHintEnabled = true
      },
      {
        val startupManager = project.getServiceIfCreated(StartupManager::class.java)
        if (startupManager != null) {
          @Suppress("DEPRECATION")
          StartupActivityTestUtil.waitForProjectActivitiesToComplete(project)
          (startupManager as StartupManagerImpl).checkCleared()
        }
      },
      { runInEdtAndWait { TestDaemonCodeAnalyzerImpl(project).cleanupAfterTest() } },
      { super.tearDown() },
    )
  }

  override fun createEditor(file: VirtualFile): Editor {
    virtualFileFilter.allowTreeAccessForFile(file)
    return super.createEditor(file)
  }

  @JvmOverloads
  fun doTest(filePath: String, checkWarnings: Boolean, checkInfos: Boolean, checkWeakWarnings: Boolean = false) {
    configureByFile(filePath)
    doDoTest(checkWarnings, checkInfos, checkWeakWarnings)
  }

  fun doTest(filePath: String, projectRoot: String?, checkWarnings: Boolean, checkInfos: Boolean) {
    configureByFile(filePath, projectRoot)
    doDoTest(checkWarnings, checkInfos)
  }

  fun doTest(vFile: VirtualFile, checkWarnings: Boolean, checkInfos: Boolean) {
    doTest(arrayOf(vFile), checkWarnings, checkInfos)
  }

  fun doTest(vFiles: Array<VirtualFile>, checkWarnings: Boolean, checkInfos: Boolean) {
    configureByFiles(null, *vFiles)
    doDoTest(checkWarnings, checkInfos)
  }

  fun doTest(checkWarnings: Boolean, checkInfos: Boolean, vararg files: String) {
    configureByFiles(null, *files)
    doDoTest(checkWarnings, checkInfos)
  }

  /**
   * Checks the highlighting of [file] with the markup of its text, and returns the infos of the checked severities.
   */
  @JvmOverloads
  fun doDoTest(checkWarnings: Boolean, checkInfos: Boolean, checkWeakWarnings: Boolean = false): List<HighlightInfo> {
    val document = editor!!.document
    return checkHighlighting(ExpectedHighlightingData(document, checkWarnings, checkWeakWarnings, checkInfos)).filter { info ->
      info.severity == HighlightSeverity.INFORMATION && checkInfos ||
      info.severity == HighlightSeverity.WARNING && checkWarnings ||
      info.severity == HighlightSeverity.WEAK_WARNING && checkWeakWarnings ||
      info.severity > HighlightSeverity.WARNING
    }
  }

  /**
   * Highlights [file] with the tree access filter of the fixture and checks the result with [data].
   */
  fun checkHighlighting(data: ExpectedHighlightingData): List<HighlightInfo> {
    data.init()
    runInEdtAndWait { PsiDocumentManager.getInstance(project).commitAllDocuments() }

    val filterDisposable = Disposer.newDisposable(testRootDisposable)
    PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter(virtualFileFilter, filterDisposable)
    try {
      val infos = doHighlighting()
      val text = editor!!.document.immutableCharSequence.toString()
      doCheckResult(data, infos, text)
      return infos
    }
    finally {
      PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, filterDisposable)
      Disposer.dispose(filterDisposable)
    }
  }

  /**
   * Checks the line markers and the highlighting [infos] of [file] with [data].
   */
  protected open fun doCheckResult(data: ExpectedHighlightingData, infos: Collection<HighlightInfo>, text: String) {
    val psiFile = file!!
    runInEdtAndWait {
      ActionUtil.underModalProgress(project, "") {
        //line marker tooltips are called in BGT in production
        val document = checkNotNull(PsiDocumentManager.getInstance(project).getDocument(psiFile))
        data.checkLineMarkers(psiFile, LineMarkersPass.getDisplayedLineMarkers(document, project), text)
      }
      data.checkResult(psiFile, infos, text)
    }
  }

  fun highlightErrors(): List<HighlightInfo> = doHighlighting(HighlightSeverity.ERROR)

  fun doHighlighting(minSeverity: HighlightSeverity): List<HighlightInfo> = filter(doHighlighting(), minSeverity)

  /**
   * Runs the passes of the daemon on [file] and returns the highlighting.
   * The passes skip the line markers unless [isTestLineMarkers] is `true`.
   * The background processes keep their priority during the run, as in the test run of [DaemonAnalyzerTestCase].
   */
  fun doHighlighting(): List<HighlightInfo> {
    runInEdtAndWait { PsiDocumentManager.getInstance(project).commitAllDocuments() }
    val toIgnore = ArrayList<Int>()
    if (!isTestLineMarkers) {
      toIgnore.add(Pass.LINE_MARKERS)
      toIgnore.add(Pass.SLOW_LINE_MARKERS)
    }
    if (!isExternalValidation) {
      toIgnore.add(Pass.EXTERNAL_TOOLS)
    }
    if (isForceExternalValidation) {
      toIgnore.add(Pass.LINE_MARKERS)
      toIgnore.add(Pass.SLOW_LINE_MARKERS)
      toIgnore.add(Pass.LOCAL_INSPECTIONS)
      toIgnore.add(Pass.POPUP_HINTS)
      toIgnore.add(Pass.UPDATE_ALL)
    }
    val canChange = isCanChangeDocumentDuringHighlighting
    val psiFile = file!!
    val currentEditor = editor!!
    val infos = (ProgressManager.getInstance() as CoreProgressManager).suppressAllDeprioritizationsDuringLongTestsExecutionIn(
      ThrowableComputable<List<HighlightInfo>, RuntimeException> {
        CodeInsightTestFixtureImpl.instantiateAndRun(psiFile, currentEditor, toIgnore.toIntArray(), canChange, true)
      })
    if (!canChange) {
      ReadAction.computeBlocking<Unit, RuntimeException> {
        val document = checkNotNull(PsiDocumentManager.getInstance(project).getDocument(psiFile))
        DaemonCodeAnalyzerEx.getInstanceEx(project).fileStatusMap.assertAllDirtyScopesAreNull(document)
      }
    }
    return infos
  }

  /**
   * Finds the action [intentionActionName] in the intentions at the caret of [editor] and in the quick fixes of [infos], and invokes it on the EDT.
   */
  fun findAndInvokeIntentionAction(infos: Collection<HighlightInfo>, intentionActionName: String) {
    runInEdtAndWait { findAndInvokeIntentionAction(infos, intentionActionName, editor!!, file!!) }
  }

  fun enableInspectionTool(tool: InspectionProfileEntry) {
    enableInspectionTool(project, tool, testRootDisposable)
  }

  fun enableInspectionTools(vararg tools: InspectionProfileEntry) {
    enableInspectionTools(project, testRootDisposable, *tools)
  }

  fun disableInspectionTool(shortName: String) {
    val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
    if (profile.getInspectionTool(shortName, project) != null) {
      profile.setToolEnabled(shortName, false)
    }
  }

  fun allowTreeAccessForFile(file: VirtualFile) {
    virtualFileFilter.allowTreeAccessForFile(file)
  }

  fun allowTreeAccessForAllFiles() {
    virtualFileFilter.allowTreeAccessForAllFiles()
  }

  /**
   * Returns a highlighting test of [filePaths], as `testFile` of [DaemonAnalyzerTestCase].
   * Its `test()` configures the files and checks the highlighting of [file] with [JavaExpectedHighlightingData].
   */
  fun testFile(vararg filePaths: String): HighlightTestInfo =
    object : HighlightTestInfo(testRootDisposable, *filePaths) {
      override fun doTest(): HighlightTestInfo {
        configureByFiles(projectRoot, *filePaths)
        val data = JavaExpectedHighlightingData(editor!!.document, checkWarnings, checkWeakWarnings, checkInfos)
        if (checkSymbolNames) data.checkSymbolNames()
        checkHighlighting(data)
        return this
      }
    }

  companion object {
    /**
     * Creates a [DaemonAnalyzerFixture] with a new project for each test. Declare it as an instance field.
     *
     * [localInspectionTools] gives the inspections of the test profile, as `configureLocalInspectionTools()` of [DaemonAnalyzerTestCase].
     * See [HeavyJavaCodeInsightFixture.heavyJavaCodeInsightSubFixture] for [testDataProjectDir].
     */
    @TestOnly
    @JvmStatic
    @JvmOverloads
    fun daemonAnalyzerFixture(
      testDataPath: String,
      localInspectionTools: Supplier<Array<LocalInspectionTool>> = Supplier { LocalInspectionTool.EMPTY_ARRAY },
      testDataProjectDir: Function<String, String>? = null,
    ): TestFixture<DaemonAnalyzerFixture> =
      heavyJavaCodeInsightSubFixture("daemonAnalyzerFixture", testDataProjectDir) { context ->
        DaemonAnalyzerFixture(context, testDataPath, localInspectionTools.get())
      }

    /**
     * Creates an instance of each local inspection class of [providers].
     */
    @JvmStatic
    fun createLocalInspectionTools(vararg providers: InspectionToolProvider): Array<LocalInspectionTool> =
      providers.flatMap { provider -> provider.inspectionClasses.map { InspectionTestUtil.instantiateTool(it) } }.toTypedArray()

    @JvmStatic
    fun filter(infos: List<HighlightInfo>, minSeverity: HighlightSeverity): List<HighlightInfo> = DaemonAnalyzerTestCase.filter(infos, minSeverity)

    /**
     * Finds the action [intentionActionName] in the intentions at the caret and in the quick fixes of [infos], and invokes it.
     * Call it on the EDT.
     */
    @JvmStatic
    fun findAndInvokeIntentionAction(infos: Collection<HighlightInfo>, intentionActionName: String, editor: Editor, psiFile: PsiFile) {
      val actions = getIntentionActions(infos, editor, psiFile)
      val intentionAction = LightQuickFixTestCase.findActionWithText(actions, intentionActionName)
                            ?: throw AssertionError("Could not find action '" + intentionActionName +
                                                    "'.\nAvailable actions: [" + actions.joinToString(", ") { it.text } + "]\n" +
                                                    "HighlightInfos: [" + infos.joinToString(", ") + "]")
      CodeInsightTestFixtureImpl.invokeIntention(intentionAction, psiFile, editor)
    }

    /**
     * Returns the action [intentionActionName] of the intentions at the caret and of the quick fixes of [infos], or `null`.
     * Call it on the EDT.
     */
    @JvmStatic
    fun findIntentionAction(infos: Collection<HighlightInfo>, intentionActionName: String, editor: Editor, psiFile: PsiFile): IntentionAction? =
      LightQuickFixTestCase.findActionWithText(getIntentionActions(infos, editor, psiFile), intentionActionName)

    /**
     * Returns the intentions at the caret and the available quick fixes of [infos]. Call it on the EDT.
     */
    @JvmStatic
    fun getIntentionActions(infos: Collection<HighlightInfo>, editor: Editor, psiFile: PsiFile): List<IntentionAction> {
      val actions = LightQuickFixTestCase.getAvailableActions(editor, psiFile)
      val quickFixActions = ArrayList<IntentionAction>()
      for (info in infos) {
        info.findRegisteredQuickFix<Any?> { descriptor, _ ->
          val action = descriptor.action
          if (!actions.contains(action) && action.isAvailable(psiFile.project, editor, psiFile)) {
            quickFixActions.add(action)
          }
          null
        }
      }
      return actions + quickFixActions
    }
  }
}
