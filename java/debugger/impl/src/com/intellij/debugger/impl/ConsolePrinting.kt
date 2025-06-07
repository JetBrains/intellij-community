// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.build.BuildView
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.AsyncStacksUtils
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.ui.breakpoints.SyntheticLineBreakpoint
import com.intellij.execution.filters.ConsoleDependentFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.HyperlinkInfoFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.isLineBreak
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.JBColor
import com.intellij.util.ui.NamedColorUtil
import com.sun.jdi.*
import com.sun.jdi.event.LocatableEvent
import kotlin.collections.component1
import kotlin.collections.component2

// FIXME: introduce some registry
private val isEnabled: Boolean
  get() = Registry.`is`("debugger.navigation.from.console.prototype")


private data class SessionData(val session: DebuggerSession, val consoleView: ConsoleView) {
  // TODO: synchronize ops with this list
  val toBePrinted = mutableListOf<Pair<String, SourcePosition>>()
}

// TODO: synchronize ops with this list
private val bySession = mutableMapOf<DebuggerSession, SessionData>()
private val byConsole = mutableMapOf<ConsoleView, SessionData>()

private val ONLY_FILTERED_POSITION = object : SourcePosition() {
  override fun getFile() = throw UnsupportedOperationException()
  override fun getElementAt() = throw UnsupportedOperationException()
  override fun getLine() = throw UnsupportedOperationException()
  override fun getOffset() = throw UnsupportedOperationException()
  override fun openEditor(requestFocus: Boolean) = throw UnsupportedOperationException()
}

private class ConsolePrintingDebuggerListener : DebuggerManagerListener {

  override fun sessionCreated(session: DebuggerSession?) {
    if (!isEnabled) return
    if (session == null || session in bySession) return

    // FIXME: is it fine?
    val consoleView = when (val ec = session.process.executionResult.executionConsole) {
      is BuildView -> ec.consoleView as? ConsoleView ?: return
      is ConsoleView -> ec
      else -> return
    }

    val sessionData = SessionData(session, consoleView)
    bySession[session] = sessionData
    byConsole[consoleView] = sessionData
  }

  override fun sessionAttached(session: DebuggerSession?) {
    if (!isEnabled) return
    if (session == null) return

    val sessionData = bySession[session] ?: return
    runOnDebugManagerThread(session) {
      FileOutputStreamWriteBreakpoint(session.project, sessionData).createRequest(session.process)
    }
  }

  override fun sessionDetached(session: DebuggerSession?) {
    if (!isEnabled) return
    if (session == null) return

    val sessionData = bySession.remove(session) ?: return
    byConsole.remove(sessionData.consoleView)
  }
}

private class FileOutputStreamWriteBreakpoint(project: Project, private val data: SessionData)
  : SyntheticLineBreakpoint(project) {

  private val className: String = "java.io.FileOutputStream"
  private val methodName: String = "write"

  init {
    suspendPolicy = DebuggerSettings.SUSPEND_NONE
  }

  override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent?): Boolean {
    if (event == null) return false

    val content = try {
      val frame = event.thread().frame(0)
      val thisObj = frame.thisObject()!!
      val fdObjField = DebuggerUtils.findField(thisObj.referenceType(), "fd")
      val fdObj = thisObj.getValue(fdObjField) as ObjectReference
      val fdIdxField = DebuggerUtils.findField(fdObj.referenceType(), "fd")
      val fdIdx = (fdObj.getValue(fdIdxField) as IntegerValue).value()

      // Only stdout and stderr are interesting.
      if (fdIdx != 1 && fdIdx != 2) return false

      val arguments = DebuggerUtilsEx.getArgumentValues(frame)
      val bufValue = arguments[0] as ArrayReference
      val bytes = when (arguments.size) {
        3 -> {
          val (off, len) = arguments.drop(1).map { (it as IntegerValue).value() }
          bufValue.getValues(off, len)
        }
        1 -> bufValue.values
        else -> error("unexpected number of arguments")
      }
      String(bytes.map { (it as ByteValue).value() }.toByteArray())
    } catch (e: Throwable) {
      fileLogger().error("unable to get printed content at FileOutputStream#write()")
      return false
    }

    val suspendContext = action.suspendContext!!
    val position = findPositionToNavigate(suspendContext, suspendContext.thread!!.frames()) ?: ONLY_FILTERED_POSITION

    content.lines().forEach {
      data.toBePrinted += Pair(it, position)
    }

    return false
  }

  override fun createRequest(debugProcess: DebugProcessImpl) {
    createOrWaitPrepare(debugProcess, className)
  }

  override fun createRequestForPreparedClass(debugProcess: DebugProcessImpl, classType: ReferenceType) {
    DebuggerUtilsEx.declaredMethodsByName(classType, methodName).forEach { method ->
      if (!method.isNative) {
        assert(!method.isAbstract)
        createRequestInMethod(debugProcess, method)
      }
    }
  }

  private fun createRequestInMethod(debugProcess: DebugProcessImpl, method: Method) {
    val location = method.locationOfCodeIndex(0)
    val requestsManager = debugProcess.requestsManager
    val request = requestsManager.createBreakpointRequest(this, location)
    requestsManager.enableRequest(request)
  }

  override fun getDisplayName(): String = "Who looks at me?" // FIXME

  override fun getEventMessage(event: LocatableEvent?): String = ""
}

// FIXME: copypasted from spring?
// FIXME: why executeOnDMT is silently ignored?!
private fun runOnDebugManagerThread(javaDebuggerSession: DebuggerSession, runnable: () -> Unit) {
  javaDebuggerSession.process.managerThread.invoke(PrioritizedTask.Priority.HIGH) {
    runnable()
  }
}

private fun getNonFilteredPosition(debugProcess: DebugProcessImpl, location: Location): SourcePosition? {
  if (DebugProcessImpl.isPositionFiltered(location)) {
    return null
  }

  val position = debugProcess.positionManager.getSourcePosition(location) ?: return null
  if (DebuggerUtilsEx.isInLibraryContent(position.file.virtualFile, debugProcess.project)) {
    return null
  }

  val className = location.declaringType()?.name()
  // FIXME: how can we ignore them better?
  if (className != null && (className.startsWith("com.intellij.openapi.diagnostic.") || className == "com.intellij.idea.IdeaLogger")) {
    return null
  }

  return position
}

private fun findPositionToNavigate(suspendContext: SuspendContextImpl, frames: List<StackFrameProxyImpl>): SourcePosition? {
  val debugProcess = suspendContext.debugProcess

  for (frame in frames) {
    AsyncStacksUtils.getAgentRelatedStack(frame, suspendContext)?.let { asyncStack ->
      for (frame in asyncStack) {
        if (frame != null) {
          getNonFilteredPosition(debugProcess, frame.location())?.let { return it }
        }
      }
    }

    getNonFilteredPosition(debugProcess, frame.location())?.let { return it }
  }

  return null
}


class ConsolePrintingFilterProvider : ConsoleDependentFilterProvider() {
  override fun getDefaultFilters(consoleView: ConsoleView, project: Project, scope: GlobalSearchScope): Array<out Filter?> {
    if (!isEnabled) return Filter.EMPTY_ARRAY

    val sessionData = byConsole[consoleView] ?: return Filter.EMPTY_ARRAY
    return arrayOf(ConsolePrintingFilter(project, sessionData))
  }
}

private class ConsolePrintingFilter(private val project: Project, val sessionData: SessionData) : Filter {

  // TODO: introduce better UI, or use some color scheme like in ClassFinderFilter
  private val hyperLinkAttributes: TextAttributes =
    TextAttributes().apply {
      effectType = EffectType.BOLD_DOTTED_LINE
      effectColor = NamedColorUtil.getInactiveTextColor()
    }

  private val brokenHyperlinkAttributes =
    TextAttributes().apply {
      effectType = EffectType.WAVE_UNDERSCORE
      effectColor = JBColor.RED
    }

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val idx =
      line.trimEnd { it.isLineBreak() }.let { lineTrimmed ->
        sessionData.toBePrinted.indexOfFirst { it.first == lineTrimmed }
      }
    if (idx == -1) return null

    // TODO: optimize these ops
    val position = sessionData.toBePrinted.removeAt(idx).second

    if (position === ONLY_FILTERED_POSITION) {
      return Filter.Result(
        entireLength - line.length, entireLength,
        HyperlinkInfoFactory.getInstance()
          .createMultipleFilesHyperlinkInfo(emptyList(), 10, project),
        brokenHyperlinkAttributes,
        brokenHyperlinkAttributes,
      )
    }

    return Filter.Result(
      entireLength - line.length, entireLength,
      getHyperlinkInfo(project, position),
      hyperLinkAttributes,
      hyperLinkAttributes,
    )
  }

  private fun getHyperlinkInfo(project: Project, position: SourcePosition): HyperlinkInfo {
    return HyperlinkInfoFactory.getInstance().createMultipleFilesHyperlinkInfo(
      position.file.virtualFile.let(::listOf), position.line, project, null)
  }
}
