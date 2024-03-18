// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.customization.console

import com.intellij.analysis.customization.console.ClassFinderConsoleColorsPage.TERMINAL_CLASS_NAME_LOG_REFERENCE
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.annotations.NonNls

internal data class ProbableClassName(val from: Int,
                                      val to: Int,
                                      val fullLine: String,
                                      val fullClassName: String)

private const val EXCEPTION_IN_THREAD: @NonNls String = "Exception in thread \""
private const val CAUSED_BY: @NonNls String = "Caused by: "
private const val AT: @NonNls String = "\tat "

private const val POINT_CODE = '.'.code

private val HARDCODED_NOT_CLASS = setOf(
  "sun.awt.X11"
)

internal class ClassFinderFilter(private val myProject: Project, myScope: GlobalSearchScope) : Filter {
  private val cache = ClassInfoResolver(myProject, myScope)
  private val hyperLinkAttributes: TextAttributes = EditorColorsManager.getInstance()?.globalScheme?.getAttributes(TERMINAL_CLASS_NAME_LOG_REFERENCE)?: hardCodedHyperLinkAttributes()

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val textStartOffset = entireLength - line.length
    val expectedClasses = findProbableClasses(line)
    val results: MutableList<Filter.Result> = ArrayList()
    val attributes = hyperLinkAttributes
    for (probableClass in expectedClasses) {

      results.add(
        Filter.Result(textStartOffset + probableClass.from,
                      textStartOffset + probableClass.to,
                      getHyperLink(probableClass),
                      attributes,
                      attributes))
    }
    if (results.isEmpty()) {
      return null
    }
    return Filter.Result(results)
  }

  private fun hardCodedHyperLinkAttributes(): TextAttributes {
    val attributes = TextAttributes()
    attributes.effectType = EffectType.BOLD_DOTTED_LINE
    attributes.effectColor = NamedColorUtil.getInactiveTextColor()
    return attributes
  }


  private fun getHyperLink(probableClassName: ProbableClassName): HyperlinkInfo {
    return OnFlyMultipleFilesHyperlinkInfo(cache, probableClassName,0, myProject, LogFinderHyperlinkHandler(probableClassName))
  }

  companion object {
    private fun findProbableClasses(line: String): List<ProbableClassName> {
      if (line.isBlank() || line.startsWith(EXCEPTION_IN_THREAD) || line.startsWith(CAUSED_BY) || line.startsWith(AT)) {
        return emptyList()
      }

      val result = mutableListOf<ProbableClassName>()
      var start = -1
      var pointCount = 0
      var i = 0
      var first = true
      while (true) {
        if (!first) {
          val previousPoint = line.codePointAt(i)
          i += Character.charCount(previousPoint)
          if (i >= line.length) {
            break
          }
        }
        else {
          first = false
        }

        val ch = line.codePointAt(i)
        if (start == -1 && isJavaIdentifierStart(ch)) {
          start = i
          continue
        }
        if (start != -1 && ch == POINT_CODE) {
          pointCount++
          continue
        }
        if (start != -1 &&
            ((line.codePointAt(i - 1) == POINT_CODE && isJavaIdentifierStart(ch)) ||
             (line.codePointAt(i - 1) != POINT_CODE && isJavaIdentifierPart(ch)))) {
          val charCount = Character.charCount(ch)
          if (i + charCount >= line.length && pointCount >= 2) {
            addProbableClass(line, start, line.length, result)
          }
          continue
        }

        if (pointCount >= 2) {
          addProbableClass(line, start, i, result)
        }
        pointCount = 0
        start = -1
      }
      return result
    }

    private fun isJavaIdentifierStart(cp: Int): Boolean {
      return cp >= 'a'.code && cp <= 'z'.code || cp >= 'A'.code && cp <= 'Z'.code ||
             Character.isJavaIdentifierStart(cp)
    }

    private fun isJavaIdentifierPart(cp: Int): Boolean {
      return cp >= '0'.code && cp <= '9'.code || cp >= 'a'.code && cp <= 'z'.code || cp >= 'A'.code && cp <= 'Z'.code ||
             Character.isJavaIdentifierPart(cp)
    }

    private fun addProbableClass(line: String,
                                 startInclusive: Int,
                                 endExclusive: Int,
                                 result: MutableList<ProbableClassName>) {
      var actualEndExclusive = endExclusive
      if (actualEndExclusive > 0 && line[actualEndExclusive - 1] == '.') {
        actualEndExclusive--
      }
      val fullClassName = line.substring(startInclusive, actualEndExclusive)
      if (canBeShortenedFullyQualifiedClassName(fullClassName) && isJavaStyle(fullClassName) && !isHardcodedNotClass(fullClassName)) {
        val probableClassName = ProbableClassName(startInclusive + fullClassName.lastIndexOf(".") + 1,
                                                  startInclusive + fullClassName.length, line, fullClassName)
        result.add(probableClassName)
      }
    }

    private fun isHardcodedNotClass(fullClassName: String): Boolean {
      return HARDCODED_NOT_CLASS.contains(fullClassName)
    }

    private fun isJavaStyle(shortenedClassName: String): Boolean {
      if (shortenedClassName.isEmpty()) return false
      val indexOfSeparator = shortenedClassName.lastIndexOf('.')
      if (indexOfSeparator <= 0 || indexOfSeparator == shortenedClassName.lastIndex) return false
      return !shortenedClassName.contains("_") &&
             Character.isUpperCase(shortenedClassName[indexOfSeparator + 1]) &&
             Character.isLowerCase(shortenedClassName[0])
    }

    private fun canBeShortenedFullyQualifiedClassName(fullClassName: String): Boolean {
      var length = 0
      for (c in fullClassName) {
        if (c == '.') {
          if (length == 0) {
            return false
          }
          length = 0
        }
        else {
          length++
        }
      }
      return true
    }
  }
}
