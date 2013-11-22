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
import com.intellij.AppTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public class DelayedDocumentWatcher {

  private static final Key<CachedValue<Boolean>> CONTAINS_ERROR_ELEMENT = Key.create("CONTAINS_ERROR_ELEMENT");

  // All instance fields are be accessed from EDT
  private final Project myProject;
  private final Alarm myAlarm;
  private final int myDelayMillis;
  private final Consumer<Set<VirtualFile>> myConsumer;
  private final Condition<VirtualFile> myChangedFileFilter;
  private final MyDocumentAdapter myListener;
  private final Runnable myAlarmRunnable;

  private final Set<VirtualFile> myChangedFiles = new THashSet<VirtualFile>();
  private boolean myDocumentSavingInProgress = false;
  private MessageBusConnection myConnection;
  private int myEventCount = 0;

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
    if (myConnection == null) {
      myConnection = ApplicationManager.getApplication().getMessageBus().connect(myProject);
      myConnection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
        @Override
        public void beforeAllDocumentsSaving() {
          myDocumentSavingInProgress = true;
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              myDocumentSavingInProgress = false;
            }
          }, ModalityState.any());
        }
      });
    }
  }

  public void deactivate() {
    EditorFactory.getInstance().getEventMulticaster().removeDocumentListener(myListener);
    if (myConnection != null) {
      myConnection.disconnect();
      myConnection = null;
    }
  }

  private class MyDocumentAdapter extends DocumentAdapter {
    @Override
    public void documentChanged(DocumentEvent event) {
      if (myDocumentSavingInProgress) {
        /** When {@link FileDocumentManager#saveAllDocuments} is called,
         *  {@link com.intellij.openapi.fileEditor.impl.TrailingSpacesStripper} can change a document.
         *  These needless 'documentChanged' events should be filtered out.
         */
        return;
      }
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
      myEventCount++;
    }
  }

  private class MyRunnable implements Runnable {
    @Override
    public void run() {
      final int oldEventCount = myEventCount;
      final Set<VirtualFile> copy = ImmutableSet.copyOf(myChangedFiles);
      asyncCheckErrors(copy, new Consumer<Boolean>() {
        @Override
        public void consume(Boolean errorsFound) {
          if (myEventCount != oldEventCount) {
            // 'documentChanged' event was raised during async checking files for errors
            // Do nothing in that case, this method will be invoked subsequently.
            return;
          }
          if (errorsFound) {
            // Do nothing, if some changed file has syntax errors.
            // This method will be invoked subsequently, when syntax errors are fixed.
            return;
          }
          myChangedFiles.clear();
          myConsumer.consume(copy);
        }
      });
    }
  }

  private void asyncCheckErrors(@NotNull final Collection<VirtualFile> files,
                                @NotNull final Consumer<Boolean> errorsFoundConsumer) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final boolean errorsFound = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
          @Override
          public Boolean compute() {
            for (VirtualFile file : files) {
              if (hasErrors(file)) {
                return true;
              }
            }
            return false;
          }
        });
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            errorsFoundConsumer.consume(errorsFound);
          }
        }, ModalityState.any());
      }
    });
  }

  // This method is called in a background thread with a read lock acquired
  private boolean hasErrors(@NotNull VirtualFile file) {
    // don't use 'WolfTheProblemSolver.hasSyntaxErrors(file)' if possible
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document != null) {
      final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
      if (psiFile != null) {
        CachedValuesManager cachedValuesManager = CachedValuesManager.getManager(myProject);
        return cachedValuesManager.getCachedValue(
          psiFile,
          CONTAINS_ERROR_ELEMENT,
          new CachedValueProvider<Boolean>() {
            @Override
            public Result<Boolean> compute() {
              boolean error = PsiTreeUtil.hasErrorElements(psiFile);
              return Result.create(error, psiFile);
            }
          },
          false
        );
      }
    }
    return WolfTheProblemSolver.getInstance(myProject).hasSyntaxErrors(file);
  }

}
