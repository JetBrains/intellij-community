// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.actions.BaseCodeInsightAction
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.navigation.CtrlMouseAction
import com.intellij.codeInsight.navigation.CtrlMouseData
import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.codeInsight.navigation.action.GotoDeclarationUtil
import com.intellij.find.actions.ShowUsagesAction
import com.intellij.injected.editor.EditorWindow
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl.Companion.actionEventData
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.ex.ActionUtil.getActionUnavailableMessage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorGutter
import com.intellij.openapi.editor.elf.Elf
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiUtilBase
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.text.MessageFormat

open class GotoDeclarationAction : BaseCodeInsightAction(), DumbAware, CtrlMouseAction {
  override fun actionPerformed(e: AnActionEvent) {
    val file = e.getData(CommonDataKeys.PSI_FILE)
    val language = file?.getLanguage()
    val currentEventData = ContainerUtil.append(
      actionEventData(e),
      EventFields.CurrentFile.with(language)
    )
    val savedEventData = ourCurrentEventData
    ourCurrentEventData = currentEventData
    val patchedEvent = getEventWithReporter(e)
    try {
      super.actionPerformed(patchedEvent)
    }
    finally {
      ourCurrentEventData = savedEventData
    }
  }

  override fun getHandler(): CodeInsightActionHandler =
    GotoDeclarationOrUsageHandler2(null)

  internal fun getReporter(dataContext: DataContext): GotoDeclarationReporter? =
    GO_TO_DECLARATION_REPORTER_DATA_KEY.getData(dataContext)

  override fun getHandler(dataContext: DataContext): CodeInsightActionHandler =
    GotoDeclarationOrUsageHandler2(getReporter(dataContext))

  override fun getCtrlMouseData(editor: Editor, file: PsiFile, offset: Int): CtrlMouseData? =
    GotoDeclarationOrUsageHandler2.getCtrlMouseData(editor, file, offset)

  override fun update(event: AnActionEvent) {
    val inputEvent = event.inputEvent
    val isMouseShortcut = inputEvent is MouseEvent && event.place == ActionPlaces.MOUSE_SHORTCUT

    if (event.project == null ||
        event.getData(EditorGutter.KEY) != null ||
        !isMouseShortcut && event.getData(CommonDataKeys.EDITOR_VIRTUAL_SPACE) == true
    ) {
      LOG.trace {
        val reason = when {
          event.project == null -> "noProject"
          event.getData(EditorGutter.KEY) != null -> "gutter"
          else -> "virtualSpace"
        }
        "GotoDeclarationAction disabled: reason=$reason, place=${event.place}, isMouseShortcut=$isMouseShortcut"
      }
      event.presentation.setEnabled(false)
      return
    }

    val editor = event.getData(CommonDataKeys.EDITOR)
    if (editor != null &&
        isMouseShortcut &&
        !computeIsPointOverText(event, editor, inputEvent)
    ) {
      LOG.trace { "GotoDeclarationAction disabled: reason=pointNotOverText, place=${event.place}" }
      event.presentation.setEnabled(false)
      return
    }

    for (handler in GotoDeclarationHandler.EP_NAME.extensionList) {
      val text = handler.getActionText(event.dataContext) ?: continue
      val presentation = event.presentation
      presentation.text = text
      break
    }

    super.update(event)

    logUpdateStatus(event, inputEvent, isMouseShortcut)
  }

  /**
   * Diagnostic logging for IJPL: GotoDeclaration intermittently disabled while it should be available (notably when switching to an existing file from another).
   * All output is TRACE-only and off by default; enable #com.intellij.codeInsight.navigation.actions.GotoDeclarationAction:trace to collect it.
   *
   */
  private fun logUpdateStatus(
    event: AnActionEvent,
    inputEvent: InputEvent?,
    isMouseShortcut: Boolean,
  ) {
    if (event.presentation.isEnabled) {
      LOG.trace { "GotoDeclarationAction enabled: place=${event.place}" }
    }
    else {
      LOG.trace {
        val project = event.project
        val baseEditor = event.getData(CommonDataKeys.EDITOR)
        val inElfScope = Elf.getElf().isInElfScope()
        val lookupActive = project != null && LookupManager.getInstance(project).activeLookup != null
        val document = baseEditor?.document
        val pdm = project?.let { PsiDocumentManager.getInstance(it) }
        val committed = if (pdm != null && document != null) pdm.isCommitted(document) else null
        // Sub-causes of a null result from getPsiFileInEditor, computed with public APIs:
        val cachedPsi = if (pdm != null && document != null) pdm.getCachedPsiFile(document) else null
        val psiForDoc = if (pdm != null && document != null) pdm.getPsiFile(document) else null // classic, no context
        // Identities let us confirm the SAME document/file keeps failing across caret moves (stickiness):
        val editorId = if (baseEditor == null) 0 else System.identityHashCode(baseEditor)
        val docId = if (document == null) 0 else System.identityHashCode(document)
        val vFile = if (document == null) null else FileDocumentManager.getInstance().getFile(document)
        // getPsiFileInEditor may THROW on an invalid file (ensureValid) — that is itself the disable cause:
        var psiInEditorError: String? = null
        val psiInEditor = try {
          val f = if (project != null && baseEditor != null) {
            PsiUtilBase.getPsiFileInEditor(baseEditor, project)
          }
          else null
          if (f == null) "null" else "${f.language.id},valid=${f.isValid}"
        }
        catch (t: Throwable) {
          psiInEditorError = "${t.javaClass.name}: ${t.message}"
          "THREW"
        }
        val caretOffset = baseEditor?.caretModel?.offset ?: -1
        "GotoDeclarationAction disabled (post-super): " +
        "place=${event.place}" +
        ", updateThread=${Thread.currentThread().name}" +
        ", inputEvent=${inputEvent?.javaClass?.simpleName ?: "null"}" +
        ", isMouseShortcut=$isMouseShortcut" +
        ", fromContextMenu=${event.isFromContextMenu}" +
        ", fromActionToolbar=${event.isFromActionToolbar}" +
        ", project=${project != null}" +
        ", editor=${baseEditor != null}@$editorId" +
        ", editorClass=${baseEditor?.javaClass?.name ?: "null"}" +
        ", document@$docId" +
        ", file=${vFile?.name ?: "null"}" +
        ", caretOffset=$caretOffset" +
        ", inElfScope=$inElfScope" +
        ", lookupActive=$lookupActive" +
        ", documentCommitted=$committed" +
        ", cachedPsiFile=${if (cachedPsi == null) "null" else "${cachedPsi.language.id},valid=${cachedPsi.isValid}"}" +
        ", getPsiFile(doc)=${if (psiForDoc == null) "null" else "${psiForDoc.language.id},valid=${psiForDoc.isValid}"}" +
        ", getPsiFileInEditor=$psiInEditor" +
        (if (psiInEditorError == null) "" else ", getPsiFileInEditorError=$psiInEditorError")
      }
    }
  }

  private fun computeIsPointOverText(
    event: AnActionEvent,
    editor: Editor,
    inputEvent: MouseEvent,
  ): Boolean =
    event.updateSession.compute(this, "isPointOverText", ActionUpdateThread.EDT) {
    EditorUtil.isPointOverText(editor, RelativePoint(inputEvent).getPoint(editor.getContentComponent()))
  }

  override fun isValidForLookup(): Boolean = true

  private fun getEventWithReporter(e: AnActionEvent): AnActionEvent {
    val reporter = GotoDeclarationFUSReporter()
    val context = CustomizedDataContext.withSnapshot(e.dataContext) { sink ->
      sink[GO_TO_DECLARATION_REPORTER_DATA_KEY] = reporter
    }
    return e.withDataContext(context)
  }

  @Suppress("CompanionObjectInExtension")
  companion object {
    @Suppress("ApiStatusInternalRedundantOnInternalDeclaration")
    @JvmStatic
    @ApiStatus.Internal
    internal fun getCurrentEventData(): List<EventPair<*>> {
      ThreadingAssertions.assertEventDispatchThread()
      return requireNotNull(ourCurrentEventData)
    }

    @JvmOverloads
    @JvmStatic
    fun startFindUsages(
      editor: Editor,
      project: Project,
      element: PsiElement,
      point: RelativePoint? = null,
    ) {
      if (DumbService.getInstance(project).isDumb) {
        val action = ActionManager.getInstance().getAction(ShowUsagesAction.ID)
        val name = action.getTemplatePresentation().text
        DumbService.getInstance(project).showDumbModeNotificationForAction(
          getActionUnavailableMessage(name),
          ShowUsagesAction.ID
        )
      }
      else {
        val popupPosition = point ?: JBPopupFactory.getInstance().guessBestPopupLocation(editor)
        ShowUsagesAction.startFindUsages(element, popupPosition, editor)
      }
    }

    @TestOnly
    @JvmStatic
    fun findElementToShowUsagesOf(editor: Editor, offset: Int): PsiElement? {
      return TargetElementUtil.getInstance().findTargetElement(editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED, offset)
    }

    // returns true if processor is run or is going to be run after showing popup
    @JvmStatic
    fun chooseAmbiguousTarget(
      editor: Editor,
      offset: Int,
      processor: PsiElementProcessor<in PsiElement>,
      @NlsContexts.PopupTitle titlePattern: @NlsContexts.PopupTitle String,
      elements: Array<PsiElement>?,
    ): Boolean {
      if (TargetElementUtil.inVirtualSpace(editor, offset)) {
        return false
      }

      val ref = Ref.create<PsiReference?>()
      return PsiTargetNavigator {
        val reference = TargetElementUtil.findReference(editor, offset)
        ref.set(reference)
        if (elements.isNullOrEmpty()) {
          return@PsiTargetNavigator if (reference == null) emptyList() else suggestCandidates(reference)
        }
        listOf(*elements)
      }.elementsConsumer { elements, navigator ->
        val title = getTitle(titlePattern, elements, ref.get())
        navigator.title(title)
      }.navigate(editor, null) { element ->
        processor.execute(element)
      }
    }

    @NlsContexts.PopupTitle
    private fun getTitle(
      @NlsContexts.PopupTitle titlePattern: @NlsContexts.PopupTitle String,
      elements: Collection<PsiElement>,
      reference: PsiReference?,
    ): @NlsContexts.PopupTitle String {
      if (reference == null) {
        return titlePattern
      }
      val range = reference.getRangeInElement()
      val elementText = reference.getElement().getText()
      LOG.assertTrue(range.startOffset >= 0 && range.endOffset <= elementText.length, "$elements;$reference")
      val refText = range.substring(elementText)
      return MessageFormat.format(titlePattern, refText)
    }

    private fun suggestCandidates(reference: PsiReference?): Collection<PsiElement> {
      if (reference == null) {
        return emptyList()
      }
      return TargetElementUtil.getInstance().getTargetCandidates(reference)
    }

    @TestOnly
    @JvmStatic
    fun findTargetElement(project: Project, editor: Editor, offset: Int): PsiElement? {
      val targets = findAllTargetElements(project, editor, offset)
      return targets.singleOrNull()
    }

    @TestOnly
    @JvmStatic
    fun findAllTargetElements(project: Project, editor: Editor, offset: Int): Array<PsiElement> {
      if (TargetElementUtil.inVirtualSpace(editor, offset)) {
        return PsiElement.EMPTY_ARRAY
      }

      val targets = findTargetElementsNoVS(project, editor, offset, true)
      return targets ?: PsiElement.EMPTY_ARRAY
    }

    private fun findTargetElementsFromProviders(
      project: Project,
      editor: Editor,
      offset: Int,
    ): Array<PsiElement>? {
      val document = editor.document
      val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return null
      return GotoDeclarationUtil.findTargetElementsFromProviders(editor, offset, file)
    }

    @TestOnly
    @JvmStatic
    fun findTargetElementsNoVS(
      project: Project,
      editor: Editor,
      offset: Int,
      lookupAccepted: Boolean,
    ): Array<PsiElement>? {
      val fromProviders = findTargetElementsFromProviders(project, editor, offset)
      if (fromProviders == null || fromProviders.isNotEmpty()) {
        return fromProviders
      }

      var flags = TargetElementUtil.getInstance().getAllAccepted() and TargetElementUtil.ELEMENT_NAME_ACCEPTED.inv()
      if (!lookupAccepted) {
        flags = flags and TargetElementUtil.LOOKUP_ITEM_ACCEPTED.inv()
      }
      val element = TargetElementUtil.getInstance().findTargetElement(editor, flags, offset)
      if (element != null) {
        return arrayOf(element)
      }

      // if no references found in injected fragment, try outer document
      if (editor is EditorWindow) {
        return findTargetElementsNoVS(project, editor.getDelegate(), editor.getDocument().injectedToHost(offset), lookupAccepted)
      }

      return null
    }
  }
}

private val LOG = Logger.getInstance(GotoDeclarationAction::class.java)
private var ourCurrentEventData: List<EventPair<*>>? = null // accessed from EDT only
private val GO_TO_DECLARATION_REPORTER_DATA_KEY = DataKey.create<GotoDeclarationReporter>("GoToDeclarationReporterKey")
