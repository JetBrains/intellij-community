/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.execution;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class DelayedDocumentWatcher {
  private final Project myProject;
  private final Alarm myAlarm;
  private final int myDelayMillis;
  private final Consumer<Set<VirtualFile>> myConsumer;
  private final Condition<VirtualFile> myChangedFileFilter;
  private final MyDocumentAdapter myListener;
  private final Runnable myAlarmRunnable;

  private final Set<VirtualFile> myChangedFiles = new THashSet<VirtualFile>();


  public DelayedDocumentWatcher(@NotNull Project project,
                                int delayMillis,
                                @NotNull Consumer<Set<VirtualFile>> consumer,
                                @Nullable Condition<VirtualFile> changedFileFilter) {
    myProject = project;
    myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, myProject);
    myDelayMillis = delayMillis;
    myConsumer = consumer;
    myChangedFileFilter = changedFileFilter;
    myListener = new MyDocumentAdapter();
    myAlarmRunnable = new MyRunnable();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public void activate() {
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(myListener, myProject);
  }

  public void deactivate() {
    EditorFactory.getInstance().getEventMulticaster().removeDocumentListener(myListener);
  }

  private class MyDocumentAdapter extends DocumentAdapter {
    @Override
    public void documentChanged(DocumentEvent event) {
      final Document document = event.getDocument();
      final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      if (file == null) {
        return;
      }
      if (!myChangedFiles.contains(file)) {
        // optimization: if possible, avoid possible expensive 'myChangedFileFilter.value(file)' call
        if (myChangedFileFilter != null && !myChangedFileFilter.value(file)) {
          return;
        }

        myChangedFiles.add(file);
      }

      myAlarm.cancelRequest(myAlarmRunnable);
      myAlarm.addRequest(myAlarmRunnable, myDelayMillis);
    }
  }

  private class MyRunnable implements Runnable {
    @Override
    public void run() {
      WolfTheProblemSolver problemSolver = WolfTheProblemSolver.getInstance(myProject);
      for (VirtualFile file : myChangedFiles) {
        if (problemSolver.hasSyntaxErrors(file)) {
          return;
        }
      }
      Set<VirtualFile> copy = ImmutableSet.copyOf(myChangedFiles);
      myChangedFiles.clear();
      myConsumer.consume(copy);
    }
  }

}
