// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.bytecode

/**
 * Represents a JVM class declaration in a *.class file
 */
public interface JvmClassBytecodeDeclaration {
  /**
   * Qualified name of the class in binary JVM format, with `/` as a separator
   */
  public val binaryClassName: String

  /**
   * Qualified name of the top-level class containing this class, in source format, with `.` as a separator
   */
  public val topLevelSourceClassName: String
}

/**
 * Represents a JVM method or field declaration in a *.class file
 */
public interface JvmMemberBytecodeDeclaration {
  public val containingClass: JvmClassBytecodeDeclaration
  public val name: String

  /**
   * Descriptor of the method or field in JVM bytecode format.
   */
  public val descriptor: String
}

public interface JvmMethodBytecodeDeclaration : JvmMemberBytecodeDeclaration
public interface JvmFieldBytecodeDeclaration : JvmMemberBytecodeDeclaration