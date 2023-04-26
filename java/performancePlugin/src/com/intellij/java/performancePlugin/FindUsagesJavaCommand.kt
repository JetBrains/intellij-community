package com.intellij.java.performancePlugin

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.find.FindManager
import com.intellij.find.findUsages.FindUsagesHandlerFactory.OperationMode
import com.intellij.find.findUsages.FindUsagesManager
import com.intellij.find.findUsages.JavaClassFindUsagesOptions
import com.intellij.find.findUsages.JavaMethodFindUsagesOptions
import com.intellij.find.findUsages.JavaVariableFindUsagesOptions
import com.intellij.find.impl.FindManagerImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usages.Usage
import com.intellij.util.Processors
import com.jetbrains.performancePlugin.PerformanceTestSpan.TRACER
import com.jetbrains.performancePlugin.commands.FindUsagesCommand
import com.jetbrains.performancePlugin.commands.FindUsagesCommand.storeMetricsDumpFoundUsages
import com.jetbrains.performancePlugin.commands.GoToNamedElementCommand
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import java.util.*
import java.util.concurrent.CountDownLatch

class FindUsagesJavaCommand(text: String, line: Int) : AbstractCommand(text, line) {

  companion object {
    const val PREFIX = CMD_PREFIX + "findUsagesJava"
    private val LOG = Logger.getInstance(FindUsagesJavaCommand::class.java)
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback = ActionCallbackProfilerStopper()
    val arguments = text.split(" ".toRegex(), 3).toTypedArray()
    val position = arguments[1]
    val elementName = arguments[2]
    val result = GoToNamedElementCommand(GoToNamedElementCommand.PREFIX + " $position $elementName", -1).execute(context)
    result.onError {
      actionCallback.reject("fail to go to element $elementName")
    }
    val findUsagesFinished = CountDownLatch(1)
    val allUsages: List<Usage> = ArrayList()
    val project = context.project
    val span: Ref<Span> = Ref()
    DumbService.getInstance(project).smartInvokeLater(Context.current().wrap(fun() {
      val editor = FileEditorManager.getInstance(project).selectedTextEditor
      if (editor == null) {
        actionCallback.reject("The action invoked without editor")
        return
      }
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
      if (psiFile == null) {
        actionCallback.reject("Psi File is not found")
        return
      }
      val offset = editor.caretModel.offset
      val element = when (GotoDeclarationAction.findElementToShowUsagesOf(editor, offset)) {
        null -> GotoDeclarationAction.findTargetElement(project, editor, offset)
        else -> GotoDeclarationAction.findElementToShowUsagesOf(editor, offset)
      }
      if (element == null) {
        actionCallback.reject("Can't find an element under $offset offset.")
        return
      }
      val foundElementName = (element as PsiNamedElement).name
      if (elementName.isNotEmpty()) {
        check(
          foundElementName != null && foundElementName == elementName) { "Found element name $foundElementName does not correspond to expected $elementName" }
      }
      LOG.info("Command find usages is called on element $element")
      val findUsagesManager = (FindManager.getInstance(project) as FindManagerImpl).findUsagesManager
      val handler = findUsagesManager.getFindUsagesHandler(element, OperationMode.USAGES_WITH_DEFAULT_OPTIONS)
      if (handler == null) {
        actionCallback.reject("No find usage handler found for the element:" + element.text)
        return
      }
      val findUsagesOptions = when {
        element.toString().contains("PsiClass") -> JavaClassFindUsagesOptions(project).apply {
          searchScope = GlobalSearchScope.allScope(project)
        }
        element.toString().contains("PsiMethod") -> JavaMethodFindUsagesOptions(project).apply {
          searchScope = GlobalSearchScope.allScope(project)
        }
        else -> JavaVariableFindUsagesOptions(project).apply {
          searchScope = GlobalSearchScope.allScope(project)
        }
      }
      val collectProcessor = Processors.cancelableCollectProcessor(Collections.synchronizedList(allUsages))
      span.set(TRACER.spanBuilder(FindUsagesCommand.SPAN_NAME).startSpan())
      FindUsagesManager.startProcessUsages(handler, handler.primaryElements, handler.secondaryElements, collectProcessor,
                                           findUsagesOptions) { findUsagesFinished.countDown() }
    }))
    try {
      findUsagesFinished.await()
      span.get().setAttribute("number", allUsages.size.toLong())
      span.get().end()
    }
    catch (e: InterruptedException) {
      throw RuntimeException(e)
    }
    storeMetricsDumpFoundUsages(allUsages, project)
    actionCallback.setDone()
    return actionCallback.toPromise()
  }
}