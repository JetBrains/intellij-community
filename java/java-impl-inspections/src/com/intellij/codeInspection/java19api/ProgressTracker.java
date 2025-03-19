// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19api;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.NlsContexts;

class ProgressTracker {
  private ProgressIndicator myIndicator;

  private int myCount;
  private int mySize;
  private int myPhase;
  private double myUpToNow;
  private final double[] myPhases;

  public ProgressTracker(double... phases) {
    myPhases = phases;
  }

  public void startPhase(@NlsContexts.ProgressText String text, int size) {
    myIndicator.setText(text);
    myCount = 0;
    mySize = Math.min(size, 1);
  }

  public void nextPhase() {
    myUpToNow += myPhases[myPhase++];
  }

  public void increment() {
    myIndicator.setFraction(myUpToNow + myPhases[myPhase] * ++myCount / (double)mySize);
  }

  public void init(ProgressIndicator indicator) {
    myIndicator = indicator;
    myIndicator.setFraction(0);
    myIndicator.setIndeterminate(false);
  }

  public void dispose() {
    myIndicator = null;
  }
}
