// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.build.BuildView
import com.intellij.execution.filters.ConsoleDependentFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfoBase
import com.intellij.execution.ui.ConsoleView
import com.intellij.idea.AppMode
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.isLineBreak
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.NamedColorUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import java.awt.Font
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.set


@Service(Service.Level.PROJECT)
class LogCapture {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): LogCapture = project.service()

    val isEnabled: Boolean
      get() = Registry.`is`("debugger.navigation.from.console.to.sources")
              // RemDev console doesn't support proper text attributes and highlights all lines as hyperlinks which is a bit too much.
              && !AppMode.isRemoteDevHost()

    private val EMPTY_TEXT_ATTRS = TextAttributes(null, null, null, null, Font.PLAIN)

    // TODO: we might have to use some color scheme
    private val DEFAULT_HOVER_TEXT_ATTRS = TextAttributes().apply {
      effectType = EffectType.BOLD_DOTTED_LINE
      effectColor = NamedColorUtil.getInactiveTextColor()
    }
  }

  fun captureBreakpointLogs(content: String, debugSession: DebuggerSession, breakpointPosition: XSourcePosition) {
    val data = bySession[debugSession] ?: return
    content.lines().forEach {
      if (!it.isBlank()) data.capturedNotYetHighlighted += Pair(it, BreakpointNavigationInfo(breakpointPosition))
    }
  }

  private class SessionData {
    val capturedNotYetHighlighted = ConcurrentLinkedQueue<Pair<String, NavigationInfo>>()
  }

  private val bySession = mutableMapOf<DebuggerSession, SessionData>()
  private val byConsole = mutableMapOf<ConsoleView, SessionData>()

  private sealed interface NavigationInfo
  private class BreakpointNavigationInfo(val position: XSourcePosition) : NavigationInfo

  internal class DebuggerListener : DebuggerManagerListener {

    override fun sessionCreated(session: DebuggerSession?) {
      if (!isEnabled) return
      if (session == null) return

      with(getInstance(session.project)) {
        if (session in bySession) return

        // TODO: find a better way to obtain console view
        val consoleView = when (val ec = session.process.executionResult.executionConsole) {
          is BuildView -> ec.consoleView as? ConsoleView ?: return
          is ConsoleView -> ec
          else -> return
        }

        val sessionData = SessionData()
        bySession[session] = sessionData
        byConsole[consoleView] = sessionData

        Disposer.register(consoleView) {
          byConsole.remove(consoleView)
        }
      }
    }

    override fun sessionDetached(session: DebuggerSession?) {
      if (!isEnabled) return
      if (session == null) return

      with(getInstance(session.project)) {
        bySession.remove(session)
      }
    }
  }

  internal class ConsoleFilterProvider : ConsoleDependentFilterProvider() {
    override fun getDefaultFilters(consoleView: ConsoleView, project: Project, scope: GlobalSearchScope): Array<out Filter?> {
      if (!isEnabled) return Filter.EMPTY_ARRAY

      return arrayOf(ConsoleFilter(consoleView, project))
    }
  }

  private class ConsoleFilter(val consoleView: ConsoleView, val project: Project) : Filter {
    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
      // We shouldn't search for this data earlier,
      // because DebuggerLister.sessionCreated() might not yet be called.
      // TODO: is it expensive? Should it be somehow checked in advance in ConsoleFilterProvider?
      val sessionData = getInstance(project).byConsole[consoleView] ?: return null

      val lineTrimmed = line.trimEnd { it.isLineBreak() }

      // TODO: optimize this search
      // TODO: remove old captured but not printed lines
      val (_, info) = sessionData.capturedNotYetHighlighted
                        .find { it.first == lineTrimmed }
                        ?.also { sessionData.capturedNotYetHighlighted.remove(it) }
                        ?: return null

      val position = when (info) {
        is BreakpointNavigationInfo -> info.position
      }

      val hyperlinkInfo = object : HyperlinkInfoBase() {
        override fun navigate(project: Project, hyperlinkLocationPoint: RelativePoint?) {
          XDebuggerUtilImpl.createNavigatable(project, position)
            .navigate(true)
        }
      }

      val lineBreakSuffixLen = line.takeLastWhile { it.isLineBreak() }.length
      return Filter.Result(
        entireLength - line.length, entireLength - lineBreakSuffixLen,
        hyperlinkInfo,
        /* highlightAttributes = */ EMPTY_TEXT_ATTRS,
        /* followedHyperlinkAttributes = */ DEFAULT_HOVER_TEXT_ATTRS, // it should be EMPTY, but IJPL-217218
        /* hoveredHyperlinkAttributes = */ DEFAULT_HOVER_TEXT_ATTRS
      )
    }
  }

}
