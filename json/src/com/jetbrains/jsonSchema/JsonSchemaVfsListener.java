// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
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
import com.intellij.util.Alarm.ThreadToUse;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public final class JsonSchemaVfsListener extends BulkVirtualFileListenerAdapter {
  public static final Topic<Runnable> JSON_SCHEMA_CHANGED = Topic.create("JsonSchemaVfsListener.Json.Schema.Changed", Runnable.class);
  public static final Topic<Runnable> JSON_DEPS_CHANGED = Topic.create("JsonSchemaVfsListener.Json.Deps.Changed", Runnable.class);

  public static @NotNull JsonSchemaUpdater startListening(@NotNull Project project, @NotNull JsonSchemaService service, @NotNull MessageBusConnection connection) {
    final JsonSchemaUpdater updater = new JsonSchemaUpdater(project, service);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new JsonSchemaVfsListener(updater));
    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeAnyChangeAbstractAdapter() {
      @Override
      protected void onChange(@Nullable PsiFile file) {
        if (file != null) updater.onFileChange(file.getViewProvider().getVirtualFile());
      }
    }, (Disposable)service);
    return updater;
  }

  private JsonSchemaVfsListener(@NotNull JsonSchemaUpdater updater) {
    super(new VirtualFileContentsChangedAdapter() {
      private final @NotNull JsonSchemaUpdater myUpdater = updater;
      @Override
      protected void onFileChange(final @NotNull VirtualFile schemaFile) {
        myUpdater.onFileChange(schemaFile);
      }

      @Override
      protected void onBeforeFileChange(@NotNull VirtualFile schemaFile) {
        myUpdater.onFileChange(schemaFile);
      }
    });
  }

  public static final class JsonSchemaUpdater {
    private static final int DELAY_MS = 200;

    private final @NotNull Project myProject;
    private final ZipperUpdater myUpdater;
    private final @NotNull JsonSchemaService myService;
    private final Set<VirtualFile> myDirtySchemas = ConcurrentCollectionFactory.createConcurrentSet();
    private final Runnable myRunnable;
    private final ExecutorService myTaskExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Json Vfs Updater Executor");

    private JsonSchemaUpdater(@NotNull Project project, @NotNull JsonSchemaService service) {
      Disposable disposable = (Disposable)service;

      myProject = project;
      myUpdater = new ZipperUpdater(DELAY_MS, ThreadToUse.POOLED_THREAD, disposable);
      myService = service;
      myRunnable = () -> {
        if (myProject.isDisposed()) return;
        Collection<VirtualFile> scope = new HashSet<>(myDirtySchemas);
        if (ContainerUtil.exists(scope, f -> service.possiblyHasReference(f.getName()))) {
          myProject.getMessageBus().syncPublisher(JSON_DEPS_CHANGED).run();
          JsonDependencyModificationTracker.forProject(myProject).incModificationCount();
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
        Arrays.stream(editors)
              .filter(editor -> editor instanceof EditorEx && editor.getProject() == myProject)
              .map(editor -> editor.getVirtualFile())
              .filter(file -> file != null && file.isValid())
              .forEach(file -> {
                final Collection<VirtualFile> schemaFiles = ((JsonSchemaServiceImpl)myService).getSchemasForFile(file, false, true);
                if (ContainerUtil.exists(schemaFiles, finalScope::contains)) {
                  if (ApplicationManager.getApplication().isUnitTestMode()) {
                    ReadAction.run(() -> restartAnalyzer(analyzer, psiManager, file));
                  }
                  else {
                    ReadAction.nonBlocking(() -> restartAnalyzer(analyzer, psiManager, file))
                      .expireWith(disposable)
                      .submit(myTaskExecutor);
                  }
                }
              });
      };
    }

    private static void restartAnalyzer(@NotNull DaemonCodeAnalyzer analyzer, @NotNull PsiManager psiManager, @NotNull VirtualFile file) {
      PsiFile psiFile = !psiManager.isDisposed() && file.isValid() ? psiManager.findFile(file) : null;
      if (psiFile != null) analyzer.restart(psiFile);
    }

    private void onFileChange(final @NotNull VirtualFile schemaFile) {
      if (JsonFileType.DEFAULT_EXTENSION.equals(schemaFile.getExtension())) {
        myDirtySchemas.add(schemaFile);
        Application app = ApplicationManager.getApplication();
        if (app.isUnitTestMode()) {
          app.invokeLater(myRunnable, myProject.getDisposed());
        }
        else {
          myUpdater.queue(myRunnable);
        }
      }
    }
  }
}
