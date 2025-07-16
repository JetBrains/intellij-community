// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.inferNullity;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import org.jetbrains.annotations.NotNull;

class AnnotateTask implements SequentialTask {
  private final Project myProject;
  private final UsageInfo[] myInfos;
  private final SequentialModalProgressTask myTask;
  private int myCount;
  private int myAdded = 0;
  private final int myTotal;
  private final NullableNotNullManager myNotNullManager;

  AnnotateTask(Project project, SequentialModalProgressTask progressTask, UsageInfo @NotNull [] infos) {
    myProject = project;
    myInfos = infos;
    myNotNullManager = NullableNotNullManager.getInstance(myProject);
    myTask = progressTask;
    myTotal = infos.length;
  }

  @Override
  public boolean isDone() {
    return myCount > myTotal - 1;
  }

  @Override
  public boolean iteration() {
    final ProgressIndicator indicator = myTask.getIndicator();
    if (indicator != null) {
      indicator.setFraction(((double)myCount) / myTotal);
    }

    if (NullityInferrer.apply(myNotNullManager, myInfos[myCount++])) {
      myAdded++;
    }

    return isDone();
  }

  int getAddedCount() {
    return myAdded;
  }
}