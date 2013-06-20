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
import com.intellij.openapi.util.Conditions;
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
  private final Project project;
  private final Alarm alarm;
  private final Consumer<VirtualFile[]> consumer;
  private final int delay;

  private final MyDocumentAdapter listener;

  private final Set<VirtualFile> changedFiles = new THashSet<VirtualFile>();
  private boolean wasRequested;

  private final Condition<VirtualFile> documentChangedFilter;

  private MessageBusConnection messageBusConnection;

  public DelayedDocumentWatcher(Project project,
                                Alarm alarm,
                                int delay,
                                Consumer<VirtualFile[]> consumer,
                                Condition<VirtualFile> documentChangedFilter) {
    this.project = project;
    this.alarm = alarm;
    this.delay = delay;
    this.consumer = consumer;
    this.documentChangedFilter = documentChangedFilter;

    listener = new MyDocumentAdapter();
  }

  public DelayedDocumentWatcher(Project project,
                                Alarm alarm,
                                int delay,
                                Consumer<VirtualFile[]> consumer) {
    this(project, alarm, delay, consumer, Conditions.<VirtualFile>alwaysTrue());
  }

  @Override
  public void run() {
    final VirtualFile[] files;
    synchronized (changedFiles) {
      wasRequested = false;
      files = changedFiles.toArray(new VirtualFile[changedFiles.size()]);
      changedFiles.clear();
    }

    final WolfTheProblemSolver problemSolver = WolfTheProblemSolver.getInstance(project);
    for (VirtualFile file : files) {
      if (problemSolver.hasSyntaxErrors(file)) {
        // threat any other file in queue as dependency of this file — don't flush if some of the queued file is invalid
        // Vladimir.Krivosheev AutoTestManager behavior behavior is preserved.
        // LiveEdit version used another strategy (flush all valid files), but now we use AutoTestManager-inspired strategy
        synchronized (changedFiles) {
          Collections.addAll(changedFiles, files);
        }

        return;
      }
    }

    consumer.consume(files);
  }

  public Project getProject() {
    return project;
  }

  public void activate() {
    if (messageBusConnection != null) {
      return;
    }

    messageBusConnection = project.getMessageBus().connect();
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener.Adapter() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event instanceof VFileDeleteEvent) {
            synchronized (changedFiles) {
              changedFiles.remove(event.getFile());
            }
          }
        }
      }
    });

    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(listener, project);
  }

  public void deactivate() {
    if (messageBusConnection == null) {
      return;
    }

    try {
      EditorFactory.getInstance().getEventMulticaster().removeDocumentListener(listener);
    }
    finally {
      messageBusConnection.disconnect();
      messageBusConnection = null;
    }
  }

  private class MyDocumentAdapter extends DocumentAdapter {
    @Override
    public void documentChanged(DocumentEvent event) {
      final Document document = event.getDocument();
      final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      if (file == null || !documentChangedFilter.value(file)) {
        return;
      }

      synchronized (changedFiles) {
        // changedFiles contains is not enough, because it can contain not-flushed files from prev request (which are not flushed because some is invalid)
        if (!changedFiles.add(file) && wasRequested) {
          return;
        }
      }

      alarm.cancelRequest(DelayedDocumentWatcher.this);
      alarm.addRequest(DelayedDocumentWatcher.this, delay);
    }
  }
}