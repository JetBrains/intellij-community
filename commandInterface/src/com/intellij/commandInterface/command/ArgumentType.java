// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.command;

/**
 * Argument type to be used with {@link Argument}
 * @author Ilya.Kazakevich
 */
public enum ArgumentType {
  /**
   * String (actually, anything)
   */
  STRING,
  /**
   * Integer or long
   */
  INTEGER
}
