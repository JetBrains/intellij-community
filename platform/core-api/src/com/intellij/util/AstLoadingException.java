// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

/**
 * @see AstLoadingFilter
 */
class AstLoadingException extends Exception {

  AstLoadingException() {
    super("See com.intellij.util.AstLoadingFilter documentation for details");
  }
}
