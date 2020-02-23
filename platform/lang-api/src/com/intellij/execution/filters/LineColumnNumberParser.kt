// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters

import com.intellij.openapi.util.text.LineColumn

/**
 * Parse line/column number from a specific text (usually console output), for example:
 * <pre>
 * [ERROR] src/main/java/MyClass.java:[1,8] (imports) UnusedImports: Unused import - java.util.Map
 * [ERROR] /tmp/my-project/src/main/java/MyClass.java:1:8: Unused import - java.util.concurrent.lock.Lock. [UnusedImports]
 * /tmp/my-project/src/main/kotlin/MyKotlin.kt:1:10: Missing spacing after ":"
 * </pre>
 */
enum class LineColumnNumberParser {
  COMPOSITE {
    override fun parse(text: String): LineColumn? {
      for (i in 1 until values().size) {
        values()[i].parse(text)?.also { return it }
      }
      return null
    }
  },
  BRACKET_COMMA_BRACKET {
    // Example: src/main/java/MyClass.java:[1,8]
    //          src/main/java/MyClass.java:[1]
    // maven-checkstyle-plugin
    // https://github.com/apache/maven-checkstyle-plugin/blob/17c66b8824eef1c13837676716cf305740a830f0/src/main/java/org/apache/maven/plugins/checkstyle/CheckstyleViolationCheckMojo.java#L648
    private val pattern = "\\[(\\d+)(,(\\d+))?\\]".toPattern()

    override fun parse(text: String): LineColumn? {
      return pattern.matcher(text).takeIf { it.find() }?.let {
        LineColumn.of(toLineColumnNumber(it.group(1)), toLineColumnNumber(it.group(3)))
      }
    }
  },
  COLON_COLON_COLON {
    // Example: /tmp/my-project/src/main/kotlin/MyKotlin.kt:1:10:
    //          [ERROR] /Users/athos/Downloads/checkstyle-nolink/src/main/java/Example.java:9: Line matches the illegal pattern
    // ktlint & checkstyle
    // https://github.com/checkstyle/checkstyle/blob/a73ff0890c27cbe8affc1a55f2710a231687bd85/src/main/java/com/puppycrawl/tools/checkstyle/AuditEventDefaultFormatter.java#L43
    // https://github.com/pinterest/ktlint/blob/4f764cd60b50c13403571e48f784b04bae4e63d7/ktlint-reporter-plain/src/main/kotlin/com/github/shyiko/ktlint/reporter/plain/PlainReporter.kt#L12
    private val pattern = ":(\\d+)(:(\\d+))?:".toPattern()

    override fun parse(text: String): LineColumn? {
      return pattern.matcher(text).takeIf { it.find() }?.let {
        LineColumn.of(toLineColumnNumber(it.group(1)), toLineColumnNumber(it.group(3)))
      }
    }
  }
  ;

  abstract fun parse(text: String): LineColumn?

  fun toLineColumnNumber(text: String?) = text?.toIntOrNull()?.let { it - 1 } ?: 0
}

