/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.jsonSchema;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ZipperUpdater;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Irina.Chernushina on 3/30/2016.
 */
public class JsonSchemaVfsListener extends BulkVirtualFileListenerAdapter {
  public static void startListening(@NotNull Project project, @NotNull final JsonSchemaServiceEx service) {
    final MyUpdater updater = new MyUpdater(project, service);
    ApplicationManager.getApplication().getMessageBus().connect(project)
      .subscribe(VirtualFileManager.VFS_CHANGES, new JsonSchemaVfsListener(updater));
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new MyDocumentListener(updater), project);
  }

  private JsonSchemaVfsListener(@NotNull MyUpdater updater) {
    super(new VirtualFileAdapter() {
      private final MyUpdater myUpdater = updater;

      @Override
      public void contentsChanged(@NotNull VirtualFileEvent event) {
        onFileChange(event.getFile());
      }

      @Override
      public void fileCreated(@NotNull VirtualFileEvent event) {
        onFileChange(event.getFile());
      }

      @Override
      public void beforeFileDeletion(@NotNull VirtualFileEvent event) {
        onFileChange(event.getFile());
      }

      @Override
      public void beforeFileMovement(@NotNull VirtualFileMoveEvent event) {
        onFileChange(event.getFile());
      }

      @Override
      public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        onFileChange(event.getFile());
      }

      @Override
      public void fileCopied(@NotNull VirtualFileCopyEvent event) {
        onFileChange(event.getFile());
      }

      private void onFileChange(@NotNull final VirtualFile schemaFile) {
        myUpdater.onFileChange(schemaFile);
      }
    });
  }

  private static class MyDocumentListener extends DocumentAdapter {
    private final MyUpdater myUpdater;

    private MyDocumentListener(MyUpdater updater) {
      myUpdater = updater;
    }

    @Override
    public void documentChanged(DocumentEvent e) {
      final VirtualFile file = FileDocumentManager.getInstance().getFile(e.getDocument());
      if (file != null) myUpdater.onFileChange(file);
    }
  }

  private static class MyUpdater {
    @NotNull private final JsonSchemaServiceEx myService;
    private JsonSchemaMappingsProjectConfiguration myMappingsProjectConfiguration;
    private final ZipperUpdater myUpdater;
    private final Set<VirtualFile> myDirtySchemas = ContainerUtil.newConcurrentSet();
    private final Runnable myRunnable;

    protected MyUpdater(@NotNull Project project, @NotNull JsonSchemaServiceEx service) {
      myService = service;
      myMappingsProjectConfiguration = JsonSchemaMappingsProjectConfiguration.getInstance(project);
      myUpdater = new ZipperUpdater(200, Alarm.ThreadToUse.POOLED_THREAD, project);
      myRunnable = () -> {
        final Set<VirtualFile> scope = new HashSet<>(myDirtySchemas);
        myDirtySchemas.removeAll(scope);
        if (scope.isEmpty()) return;

        final DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(project);
        final PsiManager psiManager = PsiManager.getInstance(project);
        final Editor[] editors = EditorFactory.getInstance().getAllEditors();
        for (Editor editor : editors) {
          if (editor instanceof EditorEx) {
            final VirtualFile file = ((EditorEx)editor).getVirtualFile();
            if (file != null && file.isValid() && JsonFileType.INSTANCE.equals(file.getFileType())) {
              final Collection<Pair<VirtualFile, String>> collection = myService.getSchemaFilesByFile(file);
              if (collection != null && !collection.isEmpty()) {
                for (Pair<VirtualFile, String> pair : collection) {
                  if (scope.contains(pair.getFirst())) {
                    ApplicationManager.getApplication().runReadAction(() -> {
                      final PsiFile psiFile = psiManager.findFile(file);
                      if (psiFile != null) {
                        analyzer.restart(psiFile);
                      }
                    });
                    break;
                  }
                }
              }
            }
          }
        }
      };
    }

    protected void onFileChange(@NotNull final VirtualFile schemaFile) {
      if (myMappingsProjectConfiguration.isRegisteredSchemaFile(schemaFile)) {
        myService.dropProviderFromCache(schemaFile);
        myDirtySchemas.add(schemaFile);
        myUpdater.queue(myRunnable);
      }
    }
  }
}
