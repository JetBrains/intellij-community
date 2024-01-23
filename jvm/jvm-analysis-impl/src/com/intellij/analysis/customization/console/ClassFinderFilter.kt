// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.customization.console

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.HyperlinkInfoFactory
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.annotations.NonNls

internal data class ProbableClassName(val from: Int,
                                      val to: Int,
                                      val fullLine: String,
                                      val shortClassName: String,
                                      val packageName: String,
                                      val virtualFiles: List<VirtualFile>)

private val EXCEPTION_IN_THREAD: @NonNls String = "Exception in thread \""
private val CAUSED_BY: @NonNls String = "Caused by: "
private val AT: @NonNls String = "\tat "

internal class ClassFinderFilter(private val myProject: Project, myScope: GlobalSearchScope) : Filter {
  private val cache = ClassInfoCache(myProject, myScope)

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val textStartOffset = entireLength - line.length

    val expectedClasses = findProbableClasses(line, cache)
    val results: MutableList<Filter.Result> = ArrayList()
    for (probableClass in expectedClasses) {
      val attributes = hyperLinkAttributes()

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

  private fun hyperLinkAttributes(): TextAttributes {
    val attributes = TextAttributes()
    attributes.effectType = EffectType.BOLD_DOTTED_LINE
    attributes.effectColor = NamedColorUtil.getInactiveTextColor()
    return attributes
  }


  private fun getHyperLink(probableClassName: ProbableClassName): HyperlinkInfo {
    return HyperlinkInfoFactory.getInstance()
      .createMultipleFilesHyperlinkInfo(probableClassName.virtualFiles, 0, myProject, LogFinderHyperlinkHandler(probableClassName))
  }

  companion object {
    private fun findProbableClasses(line: String, cache: ClassInfoCache): List<ProbableClassName> {
      if (line.startsWith(EXCEPTION_IN_THREAD) || line.startsWith(CAUSED_BY) || line.startsWith(AT)) {
        return emptyList()
      }

      val result = mutableListOf<ProbableClassName>()
      var start = -1
      var pointCount = 0
      for (i in line.indices) {
        val c = line[i]
        if (start == -1 && StringUtil.isJavaIdentifierStart(c)) {
          start = i
          continue
        }
        if (start != -1 && c == '.') {
          pointCount++
          continue
        }
        if (start != -1 &&
            ((line[i - 1] == '.' && StringUtil.isJavaIdentifierStart(c)) ||
             (line[i - 1] != '.' && StringUtil.isJavaIdentifierPart(c)))) {
          if (i == line.lastIndex && pointCount >= 2) {
            addProbableClass(line, start, i + 1, cache, result)
          }
          continue
        }

        if (pointCount >= 2) {
          addProbableClass(line, start, i, cache, result)
        }
        pointCount = 0
        start = -1
      }
      return result
    }

    private fun addProbableClass(line: String,
                                 startInclusive: Int,
                                 endExclusive: Int,
                                 cache: ClassInfoCache,
                                 result: MutableList<ProbableClassName>) {
      var actualEndExclusive = endExclusive
      if (actualEndExclusive > 0 && line[actualEndExclusive - 1] == '.') {
        actualEndExclusive--
      }
      val fullClassName = line.substring(startInclusive, actualEndExclusive)
      if (canBeShortenedFullyQualifiedClassName(fullClassName) && isJavaStyle(fullClassName)) {
        val packageName = StringUtil.getPackageName(fullClassName)
        val className = fullClassName.substring(packageName.length + 1)
        val resolvedClasses = cache.resolveClasses(className, packageName)
        if (resolvedClasses.classes.isNotEmpty()) {
          val probableClassName = ProbableClassName(startInclusive + fullClassName.lastIndexOf(".") + 1,
                                                    startInclusive + fullClassName.length,
                                                    line, className, packageName, resolvedClasses.classes.values.toList())
          result.add(probableClassName)
        }
      }
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
