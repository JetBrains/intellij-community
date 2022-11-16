// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework

import com.intellij.execution.testframework.actions.TestDiffProvider

abstract class JvmTestDiffProvider : TestDiffProvider {
  protected fun parseStackTrace(stackStrace: String): Sequence<JavaStackFrame> {
    return stackStrace.lineSequence().mapNotNull { stackFrame ->
      if (stackFrame.isEmpty()) null else JavaStackFrame.parse(stackFrame)
    }
  }

  protected data class JavaStackFrame(
    val fqModuleName: String?,
    val fqClassName: String,
    val methodName: String,
    val location: Location
  ) {
    sealed interface Location {
      companion object {
        fun parse(location: String): Location {
          if (location == "Native Method") {
            return NativeLocation
          } else {
            val fileName = location.substringBefore(':')
            val lineNumber = location.substringAfter(':').toInt()
            return FileLocation(fileName, lineNumber)
          }
        }
      }
    }

    object NativeLocation : Location

    data class FileLocation(val fileName: String, val lineNumber: Int) : Location

    companion object {
      fun parse(line: String): JavaStackFrame {
        val strippedFrame = line.substringAfter("at ")
        val signature = strippedFrame.substringBefore('(')
        val location = Location.parse(strippedFrame.substringAfter('(').removeSuffix(")"))
        val pathToClass = signature.substringBeforeLast('.')
        val (fqModuleName, fqClassName) = if (pathToClass.contains('/')) {
          val splits = pathToClass.split('/')
          splits.first() to splits.last()
        } else {
          null to pathToClass
        }
        val methodName = signature.substringAfterLast('.')
        return JavaStackFrame(fqModuleName, fqClassName, methodName, location)
      }
    }
  }
}