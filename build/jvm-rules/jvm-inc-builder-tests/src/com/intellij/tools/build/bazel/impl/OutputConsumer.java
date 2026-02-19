package com.intellij.tools.build.bazel.impl;

public interface OutputConsumer {
  void consumeLine(String line);

  String getResult();

  static OutputConsumer lastLineConsumer() {
    return new OutputConsumer() {
      private String myLine = "";

      @Override
      public void consumeLine(String line) {
        myLine = line;
      }

      @Override
      public String getResult() {
        return myLine;
      }
    };
  }

  static OutputConsumer allLinesConsumer() {
    StringBuilder buf = new StringBuilder();
    return new OutputConsumer() {
      @Override
      public void consumeLine(String line) {
        buf.append(line).append("\n");
      }

      @Override
      public String getResult() {
        return buf.toString().trim();
      }
    };
  }
}
