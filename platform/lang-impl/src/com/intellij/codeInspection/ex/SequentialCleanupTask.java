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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

class SequentialCleanupTask implements SequentialTask {
  private static final Logger LOG = Logger.getInstance(SequentialCleanupTask.class);

  private final Project myProject;
  private final LinkedHashMap<PsiFile, List<HighlightInfo>> myResults;
  private Iterator<PsiFile> myFileIterator;
  private final SequentialModalProgressTask myProgressTask;
  private int myCount = 0;
  
  public SequentialCleanupTask(Project project, LinkedHashMap<PsiFile, List<HighlightInfo>> results, SequentialModalProgressTask task) {
    myProject = project;
    myResults = results;
    myProgressTask = task;
    myFileIterator = myResults.keySet().iterator();
  }

  @Override
  public void prepare() {}

  @Override
  public boolean isDone() {
    return myFileIterator == null || !myFileIterator.hasNext();
  }

  @Override
  public boolean iteration() {
    final ProgressIndicator indicator = myProgressTask.getIndicator();
    if (indicator != null) {
      indicator.setFraction((double) myCount++/myResults.size());
    }
    final PsiFile file = myFileIterator.next();
    final List<HighlightInfo> infos = myResults.get(file);
    Collections.reverse(infos); //sort bottom - top
    for (HighlightInfo info : infos) {
      for (final Pair<HighlightInfo.IntentionActionDescriptor, TextRange> actionRange : info.quickFixActionRanges) {
        try {
          actionRange.getFirst().getAction().invoke(myProject, null, file);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
    return true;
  }

  @Override
  public void stop() {
    myFileIterator = null;
  }
}
