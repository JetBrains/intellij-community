// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.console

import com.intellij.build.BuildConsoleUtils
import com.intellij.build.BuildTextConsoleView
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.Failure
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.OutputBuildEvent
import com.intellij.build.events.OutputReferenceEvent
import com.intellij.execution.filters.LazyFileHyperlinkInfo
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ConsoleViewWithDelegate
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.unwrapDelegate
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.IJSwingUtilities
import com.intellij.util.text.nullize
import java.util.StringJoiner
import java.util.function.Consumer
import java.util.regex.Pattern
import javax.swing.event.HyperlinkEvent
import kotlin.io.path.name

private val TAG_PATTERN = Pattern.compile("<[^>]*>")
private val A_PATTERN = Pattern.compile("<a ([^>]* )?href=[\"']([^>]*)[\"'][^>]*>")
private const val A_CLOSING = "</a>"
private val NEW_LINES = mutableSetOf<@NlsSafe String?>("<br>", "</br>", "<br/>", "<p>", "</p>", "<p/>", "<pre>", "</pre>")

internal class BuildConsoleViewImpl(
  private val project: Project,
  override val delegate: ConsoleView,
) : BuildConsoleView, ConsoleViewWithDelegate, ExecutionConsole by delegate {

  init {
    Disposer.register(this, delegate)
  }

  override fun onEvent(event: BuildEvent) {
    when (event) {
      is BuildIssueEvent -> onBuildIssueEvent(event)
      is FileMessageEvent -> onFileMessageEvent(event)
      is MessageEvent -> onMessageEvent(event)
      is OutputBuildEvent -> onOutputEvent(event)
      is OutputReferenceEvent -> onOutputReferenceEvent(event)
      else -> onBuildEvent(event)
    }
  }

  private fun onBuildIssueEvent(event: BuildIssueEvent) {
    val quickFixes = event.issue.quickFixes.associateBy { it.id }
    delegate.printHtml(event.issue.description, event.result.kind.contentType) {
      quickFixes[it.description]?.runQuickFix(project, BuildConsoleUtils.getDataContext(delegate))
    }
  }

  private fun onFileMessageEvent(event: FileMessageEvent) {
    val description = event.description
    val contentType = event.result.kind.contentType
    if (!description.isNullOrEmpty()) {
      delegate.print(description, contentType)
    }
    else {
      val hyperlinkText = getHyperlinkText(event.filePosition) ?: return
      val hyperlinkInfo = getHyperlinkInfo(project, event.filePosition) ?: return
      delegate.printHyperlink(hyperlinkText, hyperlinkInfo)
      delegate.print(": ", contentType)
      delegate.print(event.message, contentType)
    }
  }

  private fun onMessageEvent(event: MessageEvent) {
    val details = event.result.details
    if (!details.isNullOrEmpty()) {
      delegate.printHtml(details, event.result.kind.contentType, null)
    }
  }

  private fun onOutputEvent(event: OutputBuildEvent) {
    BuildTextConsoleView.print(delegate, event.message, event.outputType)
  }

  private fun onOutputReferenceEvent(@Suppress("unused") event: OutputReferenceEvent) {
    throw UnsupportedOperationException("Output reference events are not supported in multi console mode.")
  }

  override fun scrollToNodeOutput(nodeId: Any) {
    throw UnsupportedOperationException("Scroll by output reference events are not supported in multi console mode.")
  }

  override fun selectProgressOutput(nodeId: Any) {
    throw UnsupportedOperationException("Select by output reference events are not supported in multi console mode.")
  }

  override fun onFailure(nodeId: Any, failure: Failure) {
    val text = (failure.description ?: failure.message ?: failure.error?.message).nullize() ?: return
    delegate.printHtml(text, ConsoleViewContentType.ERROR_OUTPUT) {
      val notification = failure.notification ?: return@printHtml
      notification.listener?.hyperlinkUpdate(notification, it)
    }
  }

  private fun onBuildEvent(event: BuildEvent) {
    delegate.print(event.description ?: event.message, ConsoleViewContentType.SYSTEM_OUTPUT)
  }

  companion object {

    internal fun getHyperlinkText(filePosition: FilePosition): String? {
      val path = filePosition.path ?: return null
      val hyperlinkText = StringJoiner(":")
      hyperlinkText.add(path.name)
      if (filePosition.startLine > 0) {
        hyperlinkText.add((filePosition.startLine + 1).toString())
      }
      if (filePosition.startColumn > 0) {
        hyperlinkText.add((filePosition.startColumn + 1).toString())
      }
      return hyperlinkText.toString()
    }

    internal fun getHyperlinkInfo(project: Project, filePosition: FilePosition): LazyFileHyperlinkInfo? {
      val path = filePosition.path?.toCanonicalPath() ?: return null
      return LazyFileHyperlinkInfo(project, path, filePosition.startLine, filePosition.startColumn)
    }

    private fun ConsoleView.printHtml(
      text: String,
      contentType: ConsoleViewContentType,
      hyperlinkListener: Consumer<HyperlinkEvent>?,
    ) {
      var content = StringUtil.convertLineSeparators(text)
      while (true) {
        val tagMatcher = TAG_PATTERN.matcher(content)
        if (!tagMatcher.find()) {
          print(content, contentType)
          break
        }
        print(content.substring(0, tagMatcher.start()), contentType)
        val tagStart = tagMatcher.group()
        val aMatcher = A_PATTERN.matcher(tagStart)
        if (aMatcher.matches()) {
          val href = aMatcher.group(2)
          val linkEnd = content.indexOf(A_CLOSING, tagMatcher.end())
          if (linkEnd > 0) {
            val hyperlinkText = content.substring(tagMatcher.end(), linkEnd)
              .replace(TAG_PATTERN.pattern().toRegex(), "")
            printHyperlink(hyperlinkText) {
              hyperlinkListener?.accept(IJSwingUtilities.createHyperlinkEvent(href, component))
            }
            content = content.substring(linkEnd + A_CLOSING.length)
            continue
          }
        }
        if (NEW_LINES.contains(tagStart)) {
          print("\n", contentType)
        }
        else {
          print(content.substring(tagMatcher.start(), tagMatcher.end()), contentType)
        }
        content = content.substring(tagMatcher.end())
      }
      print("\n", contentType)
    }

    private val MessageEvent.Kind.contentType: ConsoleViewContentType
      get() = when (this) {
        MessageEvent.Kind.ERROR -> ConsoleViewContentType.ERROR_OUTPUT
        MessageEvent.Kind.WARNING -> ConsoleViewContentType.ERROR_OUTPUT
        MessageEvent.Kind.INFO -> ConsoleViewContentType.NORMAL_OUTPUT
        MessageEvent.Kind.SIMPLE -> ConsoleViewContentType.NORMAL_OUTPUT
        MessageEvent.Kind.STATISTICS -> ConsoleViewContentType.SYSTEM_OUTPUT
      }
  }
}