// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.bytecode

/**
 * Represents a JVM class declaration in a *.class file
 */
interface JvmClassBytecodeDeclaration {
  /**
   * Qualified name of the class in binary JVM format, with `/` as a separator
   */
  val binaryClassName: String

  /**
   * Qualified name of the top-level class containing this class, in source format, with `.` as a separator
   */
  val topLevelSourceClassName: String
}