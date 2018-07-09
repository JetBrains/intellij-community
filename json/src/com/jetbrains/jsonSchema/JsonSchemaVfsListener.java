// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ZipperUpdater;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileContentsChangedAdapter;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.Topic;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Irina.Chernushina on 3/30/2016.
 */
public class JsonSchemaVfsListener extends BulkVirtualFileListenerAdapter {
  public static final Topic<Runnable> JSON_SCHEMA_CHANGED = Topic.create("JsonSchemaVfsListener.Json.Schema.Changed", Runnable.class);

  public static void startListening(@NotNull Project project, @NotNull final JsonSchemaService service) {
    final MyUpdater updater = new MyUpdater(project, service);
    ApplicationManager.getApplication().getMessageBus().connect(project)
      .subscribe(VirtualFileManager.VFS_CHANGES, new JsonSchemaVfsListener(updater));
    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeAnyChangeAbstractAdapter() {
      @Override
      protected void onChange(@Nullable PsiFile file) {
        if (file != null) updater.onFileChange(file.getViewProvider().getVirtualFile());
      }
    });
  }

  private JsonSchemaVfsListener(@NotNull MyUpdater updater) {
    super(new VirtualFileContentsChangedAdapter() {
      private final MyUpdater myUpdater = updater;

      protected void onFileChange(@NotNull final VirtualFile schemaFile) {
        myUpdater.onFileChange(schemaFile);
      }

      @Override
      protected void onBeforeFileChange(@NotNull VirtualFile schemaFile) {
        myUpdater.onFileChange(schemaFile);
      }
    });
  }

  private static class MyUpdater {
    @NotNull private final Project myProject;
    private final ZipperUpdater myUpdater;
    @NotNull private final JsonSchemaService myService;
    private final Set<VirtualFile> myDirtySchemas = ContainerUtil.newConcurrentSet();
    private final Runnable myRunnable;

    protected MyUpdater(@NotNull Project project, @NotNull JsonSchemaService service) {
      myProject = project;
      myUpdater = new ZipperUpdater(200, Alarm.ThreadToUse.POOLED_THREAD, project);
      myService = service;
      myRunnable = () -> {
        if (myProject.isDisposed()) return;
        Collection<VirtualFile> scope = new HashSet<>(myDirtySchemas);
        myDirtySchemas.removeAll(scope);

        Collection<VirtualFile> finalScope = ContainerUtil.filter(scope, file -> myService.isApplicableToFile(file) && myService.isSchemaFile(file));
        if (finalScope.isEmpty()) return;
        myProject.getMessageBus().syncPublisher(JSON_SCHEMA_CHANGED).run();

        final DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(project);
        final PsiManager psiManager = PsiManager.getInstance(project);
        final Editor[] editors = EditorFactory.getInstance().getAllEditors();
        Arrays.stream(editors).filter(editor -> editor instanceof EditorEx)
              .map(editor -> ((EditorEx)editor).getVirtualFile())
              .filter(file -> file != null && file.isValid())
              .forEach(file -> ReadAction.run(() -> {
                final Collection<VirtualFile> schemaFiles = myService.getSchemaFilesForFile(file);
                if (schemaFiles.stream().anyMatch(finalScope::contains)) {
                  Optional.ofNullable(psiManager.findFile(file)).ifPresent(analyzer::restart);
                }
              }));
      };
    }

    protected void onFileChange(@NotNull final VirtualFile schemaFile) {
      if (JsonFileType.INSTANCE.equals(schemaFile.getFileType())) {
        myDirtySchemas.add(schemaFile);
        myUpdater.queue(myRunnable);
      }
    }
  }
}
