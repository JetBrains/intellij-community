/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

/**
 * A set of constants describing possible message categories
 */
public interface CompilerMessageCategory {
  CompilerMessageCategory ERROR = new CompilerMessageCategory() {
    public String toString() {
      return "ERROR";
    }

    public String getPresentableText() {
      return toString();
    }
  };
  CompilerMessageCategory WARNING = new CompilerMessageCategory() {
    public String toString() {
      return "WARNING";
    }
    public String getPresentableText() {
      return toString();
    }
  };
  CompilerMessageCategory INFORMATION = new CompilerMessageCategory() {
    public String toString() {
      return "INFORMATION";
    }
    public String getPresentableText() {
      return toString();
    }
  };
  CompilerMessageCategory STATISTICS = new CompilerMessageCategory() {
    public String toString() {
      return "STATISTICS";
    }
    public String getPresentableText() {
      return toString();
    }
  };

  public String getPresentableText();
}
