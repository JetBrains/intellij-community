package com.siyeh.igfixes.performance.concatenation_inside_append;

import java.io.PrintWriter;

class PrintWriterAppend {

  void foo(PrintWriter printWriter, int year, int season) {
    printWriter.append("this is intellij idea ").append(String.valueOf(year)).append(".").append(String.valueOf(season)).append(" version");
  }
}