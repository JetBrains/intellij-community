/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.hprof;

import com.intellij.diagnostic.hprof.action.SystemTempFilenameSupplier;
import com.intellij.diagnostic.hprof.analysis.AnalysisContext;
import com.intellij.diagnostic.hprof.analysis.AnalyzeClassloaderReferencesGraph;
import com.intellij.diagnostic.hprof.analysis.AnalyzeGraph;
import com.intellij.diagnostic.hprof.analysis.HProfAnalysis;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import kotlin.jvm.functions.Function2;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import static java.lang.System.out;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
class AnalyzerProgressIndicator extends EmptyProgressIndicator {

  private long myStartMillis = System.currentTimeMillis();
  private DateFormat myFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

  AnalyzerProgressIndicator() {
    super(ModalityState.NON_MODAL);
    myFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  @Override
  public void setText(String text) {
    super.setText(text);
    print(text);
  }

  @Override
  public void setText2(String text) {
    super.setText2(text);
    print("  " + text);
  }

  private void print(String text) {
    long elapsedMs = System.currentTimeMillis() - myStartMillis;
    if (elapsedMs < 0) {
      elapsedMs = 0;
    }
    out.printf("[%s] %s...%n", myFormat.format(elapsedMs), text);
  }
}

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "HardCodedStringLiteral"})
public final class Analyzer {
  public static void main(String[] args) throws IOException {
    if (args.length == 0 ||
        args.length == 1 && args[0].equals("-v")) {
      out.println();
      out.println("Usage: " + Analyzer.class.getName() + " [-v] [-plugin <plugin_id>] <hprof file>");
      System.exit(1);
    }

    Path hprofPath;
    ProgressIndicator progress;
    Function2<AnalysisContext, ProgressIndicator, String> analysisCallback;
    boolean onlyStrongReferences = false;
    boolean includeClassesAsRoots = true;
    int index = 0;
    if (args[index].equals("-v")) {
      index++;
      progress = new AnalyzerProgressIndicator();
    }
    else {
      progress = new EmptyProgressIndicator(ModalityState.NON_MODAL);
    }
    if (args[index].equals("-plugin")) {
      index++;
      String pluginId = args[index];
      analysisCallback = (analysisContext, progressIndicator) -> new AnalyzeClassloaderReferencesGraph(analysisContext, pluginId).analyze(progressIndicator);
      onlyStrongReferences = true;
      includeClassesAsRoots = false;
      index++;
    }
    else {
      analysisCallback = (analysisContext, progressIndicator) -> new AnalyzeGraph(analysisContext).analyze(progressIndicator);
    }
    hprofPath = Paths.get(args[index]);

    String report;
    try (FileChannel channel = FileChannel.open(hprofPath, StandardOpenOption.READ)) {
      HProfAnalysis analysis = new HProfAnalysis(channel, new SystemTempFilenameSupplier(), analysisCallback);
      analysis.setOnlyStrongReferences(onlyStrongReferences);
      analysis.setIncludeClassesAsRoots(includeClassesAsRoots);
      report = analysis.analyze(progress);
      progress.setText("DONE");
    }
    out.println(report);
  }
}
