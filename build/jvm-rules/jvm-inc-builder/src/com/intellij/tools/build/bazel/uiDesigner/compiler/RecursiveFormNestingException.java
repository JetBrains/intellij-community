// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tools.build.bazel.uiDesigner.compiler;

public final class RecursiveFormNestingException extends UIDesignerException{
  RecursiveFormNestingException() {
    super("Recursive form nesting is not allowed");
  }
}
