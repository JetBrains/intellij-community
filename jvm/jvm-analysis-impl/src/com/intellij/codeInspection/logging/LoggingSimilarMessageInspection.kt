// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.*
import com.intellij.codeInspection.options.OptPane
import com.intellij.java.JavaBundle
import com.intellij.java.library.JavaLibraryUtil
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.annotations.Nls
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

private const val MAX_PART_COUNT = 10

private const val WITH_THROWABLE = "withThrowable"
private const val SET_CAUSE = "setCause"

class LoggingSimilarMessageInspection : AbstractBaseUastLocalInspectionTool() {

  @JvmField
  var mySkipErrorLogLevel: Boolean = true

  @JvmField
  var myMinTextLength: Int = 5

  override fun getOptionsPane(): OptPane {
    return OptPane.pane(
      OptPane.number("myMinTextLength",
                     JvmAnalysisBundle.message("jvm.inspection.logging.similar.message.problem.min.similar.length"),
                     3, 100),
      OptPane.checkbox("mySkipErrorLogLevel",
                       JvmAnalysisBundle.message("jvm.inspection.logging.similar.message.problem.skip.on.error"))
    )
  }

  //otherwise results will be inconsistent
  override fun runForWholeFile(): Boolean {
    return true
  }

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    val project = holder.project
    val file = holder.file.originalFile.virtualFile
    if (file == null) { // IDEA-369954
      return PsiElementVisitor.EMPTY_VISITOR
    }
    val fileModule = ModuleUtilCore.findModuleForFile(file, project)
    if (!(JavaLibraryUtil.hasLibraryClass(fileModule, LoggingUtil.SLF4J_LOGGER) ||
          JavaLibraryUtil.hasLibraryClass(fileModule, LoggingUtil.LOG4J_LOGGER) ||
          JavaLibraryUtil.hasLibraryClass(fileModule, LoggingUtil.IDEA_LOGGER))) {
      return PsiElementVisitor.EMPTY_VISITOR
    }

    return UastHintedVisitorAdapter.create(holder.file.language, PlaceholderCountMatchesArgumentCountVisitor(holder, myMinTextLength),
                                           arrayOf(UFile::class.java), directOnly = true)
  }

  inner class PlaceholderCountMatchesArgumentCountVisitor(
    private val holder: ProblemsHolder,
    private val myMinTextLength: Int,
  ) : AbstractUastNonRecursiveVisitor() {

    override fun visitFile(node: UFile): Boolean {
      val calls = collectCalls(node).toMutableSet()
      if (calls.isEmpty()) return true
      val groupedCalls: List<List<UCallExpression>> = calls.groupBy { it.receiver?.tryResolve().toUElementOfType<UVariable>() }
        .values.map { group ->
          group.groupBy { it.methodName }.values
        }.flatten()
      val groups: List<List<MessageLog>> = groupedCalls.map { group ->
        group.map { MessageLog(it, collectParts(it, LOGGER_TYPE_SEARCHERS.mapFirst(it)).splitWithPlaceholders()) }
          .filter { log -> log.parts?.any { it.isConstant && it.text != null } ?: false }
      }
      for (group in groups) {
        if (group.size <= 1) continue
        var currentGroups: Set<List<MessageLog>> = setOf(group)
        //prefilter
        if (group.size > 5) {
          var firstIsTaken = true
          var minLength: Int? = group
            .mapNotNull { messageLog -> messageLog.parts }
            .filter { parts -> firstIsText(parts) }
            .minOfOrNull { parts -> parts[0].text?.length ?: 0 }


          if (minLength == null || minLength == 0) {
            firstIsTaken = false
            minLength = group
              .mapNotNull { messageLog -> messageLog.parts }
              .filter { parts -> lastIsText(parts) }
              .minOfOrNull { parts -> parts.last().text?.length ?: 0 }
          }

          if (minLength == null || minLength == 0) {
            return true
          }

          currentGroups = group
            .groupBy {
              val parts = it.parts ?: return@groupBy ""
              if (parts.isEmpty()) return@groupBy ""
              val part0 = if (firstIsTaken) parts[0] else parts.last()
              val text = part0.text
              if (!part0.isConstant || text == null || text.length < minLength) return@groupBy ""
              text.substring(0, minLength)
            }
            .values
            .toSet()
        }

        for (currentGroup in currentGroups) {
          val alreadyHasWarning = mutableSetOf<Int>()
          for (firstIndex in 0..currentGroup.lastIndex) {
            for (secondIndex in firstIndex + 1..currentGroup.lastIndex) {
              if (similar(currentGroup[firstIndex].parts, currentGroup[secondIndex].parts, myMinTextLength) &&
                !sequenceOfCalls(currentGroup[firstIndex].call, currentGroup[secondIndex].call)) {
                if (alreadyHasWarning.add(firstIndex)) {
                  registerProblem(holder, currentGroup[firstIndex].call, currentGroup[secondIndex].call)
                }
                if (alreadyHasWarning.add(secondIndex)) {
                  registerProblem(holder, currentGroup[secondIndex].call, currentGroup[firstIndex].call)
                }
              }
            }
          }
        }
      }

      return super.visitFile(node)
    }

    private fun sequenceOfCalls(call1: UCallExpression, call2: UCallExpression): Boolean {
      val commonParent = PsiTreeUtil.findCommonParent(call1.sourcePsiElement, call2.sourcePsiElement)?.toUElement()
      if (commonParent is UBlockExpression || commonParent is UMethod) {
        val uastParent1 = call1.uastParent?.uastParent
        val uastParent2 = call2.uastParent?.uastParent
        if (uastParent1 == commonParent ||
            (uastParent1?.uastParent is UIfExpression && uastParent1.uastParent?.uastParent == commonParent) ||
            (uastParent1 is UIfExpression && uastParent1.uastParent == commonParent) ||
            uastParent2 == commonParent ||
            (uastParent2?.uastParent is UIfExpression && uastParent2.uastParent?.uastParent == commonParent) ||
            (uastParent2 is UIfExpression && uastParent2.uastParent == commonParent)
        ) {
          return true
        }
      }
      return false
    }

    private fun collectCalls(file: UFile): Set<UCallExpression> {
      val result = mutableSetOf<UCallExpression>()
      file.accept(object : AbstractUastVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          val place = node.sourcePsi ?: return false
          if (SuppressionUtil.inspectionResultSuppressed(place, this@LoggingSimilarMessageInspection)) return false
          val loggerTypeSearcher = LOGGER_TYPE_SEARCHERS.mapFirst(node) ?: return false
          if (mySkipErrorLogLevel) {
            val hasSetMessage = hasSetThrowable(node, loggerTypeSearcher)
            if (hasSetMessage) return false
            val loggerLevel = LoggingUtil.getLoggerLevel(node)
            if (loggerLevel == LoggingUtil.Companion.LevelType.ERROR && loggerTypeSearcher == IDEA_PLACEHOLDERS) return false
            val valueArguments = node.valueArguments
            if (loggerTypeSearcher != SLF4J_BUILDER_HOLDER && loggerTypeSearcher != LOG4J_LOG_BUILDER_HOLDER &&
                !valueArguments.isEmpty() && hasThrowableType(valueArguments.last())) return false
          }
          result.add(node)
          return true
        }
      })
      return result
    }
  }

  private fun hasSetThrowable(
    node: UCallExpression,
    loggerType: LoggerTypeSearcher?,
  ): Boolean {
    if (loggerType == null) {
      return false
    }
    if (!(loggerType == SLF4J_BUILDER_HOLDER || loggerType == LOG4J_LOG_BUILDER_HOLDER)) {
      return false
    }
    var currentCall = node.receiver
    (0..MAX_BUILDER_LENGTH).forEach { ignore ->
      if (currentCall is UQualifiedReferenceExpression) {
        currentCall = currentCall.selector
        return@forEach
      }
      if (currentCall !is UCallExpression) {
        return false
      }
      val methodName = currentCall.methodName ?: return false
      if (methodName == WITH_THROWABLE || methodName == SET_CAUSE) {
        return true
      }
      currentCall = currentCall.receiver
    }
    return false
  }

  private fun collectParts(node: UCallExpression, searcher: LoggerTypeSearcher?): List<LoggingStringPartEvaluator.PartHolder>? {
    if (searcher == null) return null
    val arguments = node.valueArguments
    val method = node.resolveToUElement() as? UMethod ?: return null
    val parameters = method.uastParameters
    val logStringArgument: UExpression?
    if (parameters.isEmpty() || arguments.isEmpty()) {
      logStringArgument = findMessageSetterStringArg(node, searcher) ?: return null
    }
    else {
      val index = getLogStringIndex(parameters) ?: return null
      logStringArgument = arguments[index - 1]
    }

    return LoggingStringPartEvaluator.calculateValue(logStringArgument)
  }

  private fun registerProblem(holder: ProblemsHolder, current: UCallExpression, other: UCallExpression) {
    val anchor = current.sourcePsi ?: return
    val otherElement = other.sourcePsi ?: return
    val commonParent = PsiTreeUtil.findCommonParent(anchor, otherElement) ?: return
    val textRange = anchor.textRange
    val delta = commonParent.textRange?.startOffset ?: return
    holder.registerProblem(commonParent, textRange.shiftLeft(delta),
                           JvmAnalysisBundle.message("jvm.inspection.logging.similar.message.problem.descriptor"),
                           NavigateToDuplicateFix(otherElement))
  }

}

private class NavigateToDuplicateFix(call: PsiElement) : ModCommandQuickFix() {
  private val myPointer = SmartPointerManager.getInstance(call.project).createSmartPsiElementPointer(call)

  override fun getFamilyName(): @Nls String {
    return JavaBundle.message("navigate.to.duplicate.fix")
  }

  override fun perform(project: Project, descriptor: ProblemDescriptor): ModCommand {
    val element = myPointer.element
    if (element == null) return ModCommand.nop()
    return ModCommand.select(element)
  }
}

private fun List<LoggingStringPartEvaluator.PartHolder>?.splitWithPlaceholders(): List<LoggingStringPartEvaluator.PartHolder>? {
  if (this == null) return null
  val result = mutableListOf<LoggingStringPartEvaluator.PartHolder>()
  for (partHolder in this) {
    val text = partHolder.text
    if (partHolder.isConstant && text != null) {
      val withoutPlaceholders = text.split("{}")
      for ((index, clearPart) in withoutPlaceholders.withIndex()) {
        result.add(LoggingStringPartEvaluator.PartHolder(clearPart, true))
        if (index != withoutPlaceholders.lastIndex) {
          result.add(LoggingStringPartEvaluator.PartHolder(clearPart, false))
        }
      }
    }
    else {
      result.add(partHolder)
    }
  }
  return result
}

private class PartHolderIterator(private val parts: List<LoggingStringPartEvaluator.PartHolder>) {
  private var i = 0
  private var partial: String? = null
  fun hasNext(): Boolean {
    return i < parts.size
  }

  fun isText(): Boolean {
    val partHolder = parts[i]
    return partHolder.isConstant && partHolder.text != null
  }

  fun move() {
    i++
    partial = null
  }

  fun current(): String? {
    val local = partial
    if (local != null) {
      return local
    }
    val partHolder = parts[i]
    return partHolder.text
  }

  fun isFirst(): Boolean {
    return i == 0
  }

  fun move(delta: Int) {
    partial = current()?.substring(delta)
  }

  fun isLast(): Boolean {
    return i == parts.lastIndex
  }

  fun previousUnknown(): Boolean {
    return if (i == 0) {
      false
    }
    else {
      val partHolder = parts[i - 1]
      !partHolder.isConstant || partHolder.text == null
    }
  }
}

private data class MessageLog(val call: UCallExpression, val parts: List<LoggingStringPartEvaluator.PartHolder>?)

private fun similar(
  first: List<LoggingStringPartEvaluator.PartHolder>?,
  second: List<LoggingStringPartEvaluator.PartHolder>?,
  minTextLength: Int,
): Boolean {
  if (first == null || second == null) return false
  if (first.isEmpty() || second.isEmpty()) return false
  if (first.any { it.callPart != null } || second.any { it.callPart != null }) {
    val firstSetArguments = first.mapNotNull { it.callPart }.flatMap { callPart -> callPart.stringArguments }.toSet()
    val secondSetArguments = second.mapNotNull { it.callPart }.flatMap { callPart -> callPart.stringArguments }.toSet()
    if (!firstSetArguments.containsAll(secondSetArguments) || !secondSetArguments.containsAll(firstSetArguments)) {
      return false
    }
  }
  if (first.size >= MAX_PART_COUNT || second.size >= MAX_PART_COUNT) return false
  val firstIterator = PartHolderIterator(first)
  val secondIterator = PartHolderIterator(second)
  var intersection = 0

  val firstFirstIsText = firstIsText(first)
  val firstLastIsText = lastIsText(first)

  val secondFirstIsText = firstIsText(second)
  val secondLastIsText = lastIsText(second)

  if (first.size == 1 && second.size == 1 &&
      firstFirstIsText && secondFirstIsText &&
      first[0].text != second[0].text) {
    return false
  }

  if (firstFirstIsText != secondFirstIsText) return false
  if (firstLastIsText != secondLastIsText) return false

  val firstCount = first.count { it.isConstant && it.text != null }
  val secondCount = second.count { it.isConstant && it.text != null }
  if (firstCount != 0 && secondCount != 0 && firstCount != secondCount) return false

  while (firstIterator.hasNext() && secondIterator.hasNext()) {
    //example: "something {} something", `{}` is skipped here
    if (!firstIterator.isText()) {
      firstIterator.move()
      continue
    }

    //example: "something {} something", `{}` is skipped here
    if (!secondIterator.isText()) {
      secondIterator.move()
      continue
    }

    //example:
    //1:"{} some message {}"
    //2: "{} some message {}"
    //it is possible that these parts are from placeholders, but it is confusing, that's why it needs to be highlighted
    if (firstIterator.current() == secondIterator.current()) {
      intersection += firstIterator.current()?.length ?: 0
      firstIterator.move()
      secondIterator.move()
      continue
    }

    //example:
    //"Message: {}"
    //"Message: 1{}"
    //Parts "Message: " are similar and can be confusing
    if (firstIterator.isFirst() && secondIterator.isFirst()) {
      if (firstIterator.current()?.startsWith(secondIterator.current() ?: "") == true) {
        val delta = secondIterator.current()?.length ?: 0
        intersection += delta
        secondIterator.move()
        firstIterator.move(delta)
        continue
      }

      if (secondIterator.current()?.startsWith(firstIterator.current() ?: "") == true) {
        val delta = firstIterator.current()?.length ?: 0
        intersection += delta
        firstIterator.move()
        secondIterator.move(delta)
        continue
      }

      return false
    }


    //example:
    //"{} - response"
    //"{}1 - response"
    //Parts " - response: " are similar and can be confusing
    if (firstIterator.isLast() && secondIterator.isLast()) {
      if (firstIterator.current()?.endsWith(secondIterator.current() ?: "") == true) {
        val delta = secondIterator.current()?.length ?: 0
        intersection += delta
        firstIterator.move()
        secondIterator.move()
        continue
      }

      if (secondIterator.current()?.endsWith(firstIterator.current() ?: "") == true) {
        val delta = firstIterator.current()?.length ?: 0
        intersection += delta
        firstIterator.move()
        secondIterator.move()
        continue
      }

      return false
    }

    //There can be not only included parts, but intersections of these parts.
    //This intersection is skipped deliberately because it can be used in real logs

    //Example:
    //"{} something {}"
    //"{} some{}"
    if (secondIterator.previousUnknown()) {
      var delta = firstIterator.current()?.indexOf(secondIterator.current() ?: "") ?: -1
      if (delta != -1) {
        delta += secondIterator.current()?.length ?: 0
        intersection += delta
        secondIterator.move()
        firstIterator.move(delta)
        continue
      }
    }

    if (firstIterator.previousUnknown()) {
      var delta = secondIterator.current()?.indexOf(firstIterator.current() ?: "") ?: -1
      if (delta != -1) {
        delta += firstIterator.current()?.length ?: 0
        intersection += delta
        firstIterator.move()
        secondIterator.move(delta)
        continue
      }
    }

    return false
  }

  return intersection >= minTextLength
}

private fun firstIsText(parts: List<LoggingStringPartEvaluator.PartHolder>) =
  parts.isNotEmpty() && parts[0].isConstant && parts[0].text?.isNotBlank() == true

private fun lastIsText(parts: List<LoggingStringPartEvaluator.PartHolder>) =
  parts.isNotEmpty() && parts.last().isConstant && parts.last().text?.isNotBlank() == true
