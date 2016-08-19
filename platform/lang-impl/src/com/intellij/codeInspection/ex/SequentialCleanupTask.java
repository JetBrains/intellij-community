/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;

import java.util.*;

class SequentialCleanupTask implements SequentialTask {
  private static final Logger LOG = Logger.getInstance(SequentialCleanupTask.class);

  private final Project myProject;
  private final List<Pair<PsiFile, HighlightInfo>> myResults = new ArrayList<>();
  private final SequentialModalProgressTask myProgressTask;
  private int myCount = 0;
  
  public SequentialCleanupTask(Project project, LinkedHashMap<PsiFile, List<HighlightInfo>> results, SequentialModalProgressTask task) {
    myProject = project;
    for (Map.Entry<PsiFile, List<HighlightInfo>> entry : results.entrySet()) {
      PsiFile file = entry.getKey();
      List<HighlightInfo> infos = entry.getValue();
      // sort from bottom to top
      Collections.sort(infos, (info1, info2) -> info2.getStartOffset() - info1.getStartOffset());
      for (HighlightInfo info : infos) {
        myResults.add(Pair.create(file, info));
      }
    }
    myProgressTask = task;
  }

  @Override
  public void prepare() {}

  @Override
  public boolean isDone() {
    return myCount > myResults.size() - 1;
  }

  @Override
  public boolean iteration() {
    final Pair<PsiFile, HighlightInfo> pair = myResults.get(myCount++);
    final ProgressIndicator indicator = myProgressTask.getIndicator();
    if (indicator != null) {
      indicator.setFraction((double) myCount/myResults.size());
      indicator.setText("Processing " + pair.first.getName());
    }
    for (final Pair<HighlightInfo.IntentionActionDescriptor, TextRange> actionRange : pair.second.quickFixActionRanges) {
      final AccessToken token = WriteAction.start();
      try {
        actionRange.getFirst().getAction().invoke(myProject, null, pair.first);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
      finally {
        token.finish();
      }
    }
    return true;
  }

  @Override
  public void stop() {}
}
