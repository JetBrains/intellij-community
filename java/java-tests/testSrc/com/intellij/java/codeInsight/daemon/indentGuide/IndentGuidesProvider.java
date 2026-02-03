package com.intellij.java.codeInsight.daemon.indentGuide;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;

interface IndentGuidesProvider {
  @NotNull
  List<Guide> getGuides();

  class Guide {

    private final Integer startLine;
    private final Integer endLine;
    private final Integer indent;

    @Contract(pure = true)
    Guide(Integer startLine, Integer endLine, Integer indent) {
      this.startLine = startLine;
      this.endLine = endLine;
      this.indent = indent;
    }

    Integer getStartLine() {
      return startLine;
    }

    Integer getEndLine() {
      return endLine;
    }

    Integer getIndent() {
      return indent;
    }

    @Override
    public String toString() {
      return String.format("(lines: %d - %d, indent: %d)", startLine, endLine, indent);
    }
  }
}
