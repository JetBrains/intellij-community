// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.json.JsonBundle;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.Disposable;
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
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * @author Irina.Chernushina on 3/30/2016.
 */
public final class JsonSchemaVfsListener extends BulkVirtualFileListenerAdapter {
  public static final Topic<Runnable> JSON_SCHEMA_CHANGED = Topic.create("JsonSchemaVfsListener.Json.Schema.Changed", Runnable.class);
  public static final Topic<Runnable> JSON_DEPS_CHANGED = Topic.create("JsonSchemaVfsListener.Json.Deps.Changed", Runnable.class);

  public static void startListening(@NotNull Project project, @NotNull JsonSchemaService service, @NotNull MessageBusConnection connection) {
    final MyUpdater updater = new MyUpdater(project, service);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new JsonSchemaVfsListener(updater));
    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeAnyChangeAbstractAdapter() {
      @Override
      protected void onChange(@Nullable PsiFile file) {
        if (file != null) updater.onFileChange(file.getViewProvider().getVirtualFile());
      }
    }, (Disposable)service);
  }

  private JsonSchemaVfsListener(@NotNull MyUpdater updater) {
    super(new VirtualFileContentsChangedAdapter() {
      @NotNull private final MyUpdater myUpdater = updater;
      @Override
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
    private final ExecutorService myTaskExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(JsonBundle.message(
      "json.vfs.updater.executor"));

    protected MyUpdater(@NotNull Project project, @NotNull JsonSchemaService service) {
      myProject = project;
      myUpdater = new ZipperUpdater(200, Alarm.ThreadToUse.POOLED_THREAD, (Disposable)service);
      myService = service;
      myRunnable = () -> {
        if (myProject.isDisposed()) return;
        Collection<VirtualFile> scope = new HashSet<>(myDirtySchemas);
        if (scope.stream().anyMatch(f -> service.possiblyHasReference(f.getName()))) {
          myProject.getMessageBus().syncPublisher(JSON_DEPS_CHANGED).run();
        }
        myDirtySchemas.removeAll(scope);
        if (scope.isEmpty()) return;

        Collection<VirtualFile> finalScope = ContainerUtil.filter(scope, file -> myService.isApplicableToFile(file)
                                                                                 && ((JsonSchemaServiceImpl)myService).isMappedSchema(file, false));
        if (finalScope.isEmpty()) return;
        if (myProject.isDisposed()) return;
        myProject.getMessageBus().syncPublisher(JSON_SCHEMA_CHANGED).run();

        final DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(project);
        final PsiManager psiManager = PsiManager.getInstance(project);
        final Editor[] editors = EditorFactory.getInstance().getAllEditors();
        Arrays.stream(editors).filter(editor -> editor instanceof EditorEx)
              .map(editor -> ((EditorEx)editor).getVirtualFile())
              .filter(file -> file != null && file.isValid())
              .forEach(file -> {
                final Collection<VirtualFile> schemaFiles = ((JsonSchemaServiceImpl)myService).getSchemasForFile(file, false, true);
                if (schemaFiles.stream().anyMatch(finalScope::contains)) {
                  ReadAction.nonBlocking(() -> Optional.ofNullable(file.isValid() ? psiManager.findFile(file) : null).ifPresent(analyzer::restart)).submit(myTaskExecutor);
                }
              });
      };
    }

    protected void onFileChange(@NotNull final VirtualFile schemaFile) {
      if (JsonFileType.DEFAULT_EXTENSION.equals(schemaFile.getExtension())) {
        myDirtySchemas.add(schemaFile);
        myUpdater.queue(myRunnable);
      }
    }
  }
}
