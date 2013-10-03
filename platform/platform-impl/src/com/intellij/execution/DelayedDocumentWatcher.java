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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DelayedDocumentWatcher implements Runnable {
  private final Project myProject;
  private final Alarm myAlarm;
  private final Consumer<VirtualFile[]> myConsumer;
  private final int myDelay;

  private final MyDocumentAdapter myListener;

  private final Set<VirtualFile> myChangedFiles = new THashSet<VirtualFile>();
  private boolean myWasRequested;

  private final Condition<VirtualFile> myDocumentChangedFilter;

  private MessageBusConnection myMessageBusConnection;

  public DelayedDocumentWatcher(Project project,
                                Alarm alarm,
                                int delay,
                                Consumer<VirtualFile[]> consumer,
                                Condition<VirtualFile> documentChangedFilter) {
    myProject = project;
    myAlarm = alarm;
    myDelay = delay;
    myConsumer = consumer;
    myDocumentChangedFilter = documentChangedFilter;

    myListener = new MyDocumentAdapter();
  }

  @Override
  public void run() {
    final VirtualFile[] files;
    synchronized (myChangedFiles) {
      myWasRequested = false;
      files = myChangedFiles.toArray(new VirtualFile[myChangedFiles.size()]);
      myChangedFiles.clear();
    }

    final WolfTheProblemSolver problemSolver = WolfTheProblemSolver.getInstance(myProject);
    for (VirtualFile file : files) {
      if (problemSolver.hasSyntaxErrors(file)) {
        // threat any other file in queue as dependency of this file — don't flush if some of the queued file is invalid
        // Vladimir.Krivosheev AutoTestManager behavior behavior is preserved.
        // LiveEdit version used another strategy (flush all valid files), but now we use AutoTestManager-inspired strategy
        synchronized (myChangedFiles) {
          Collections.addAll(myChangedFiles, files);
        }

        return;
      }
    }

    myConsumer.consume(files);
  }

  public Project getProject() {
    return myProject;
  }

  public void activate() {
    if (myMessageBusConnection != null) {
      return;
    }

    myMessageBusConnection = myProject.getMessageBus().connect(myProject);
    myMessageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener.Adapter() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event instanceof VFileDeleteEvent) {
            synchronized (myChangedFiles) {
              myChangedFiles.remove(event.getFile());
            }
          }
        }
      }
    });

    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(myListener, myProject);
  }

  public void deactivate() {
    if (myMessageBusConnection == null) {
      return;
    }

    try {
      EditorFactory.getInstance().getEventMulticaster().removeDocumentListener(myListener);
    }
    finally {
      myMessageBusConnection.disconnect();
      myMessageBusConnection = null;
    }
  }

  private class MyDocumentAdapter extends DocumentAdapter {
    @Override
    public void documentChanged(DocumentEvent event) {
      final Document document = event.getDocument();
      final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      if (file == null || !myDocumentChangedFilter.value(file)) {
        return;
      }

      synchronized (myChangedFiles) {
        // changedFiles contains is not enough, because it can contain not-flushed files from prev request (which are not flushed because some is invalid)
        if (!myChangedFiles.add(file) && myWasRequested) {
          return;
        }
      }

      myAlarm.cancelRequest(DelayedDocumentWatcher.this);
      myAlarm.addRequest(DelayedDocumentWatcher.this, myDelay);
    }
  }

}
