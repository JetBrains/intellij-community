// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.library;

import com.intellij.AppTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.ForcefulReparseModificationTracker;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * Use this modification tracker for {@link com.intellij.psi.util.CachedValue} which may contain {@link com.intellij.psi.PsiElement}
 * instances from libraries only. If a cache value contains only simple non-PsiElement values, e.g. String, List of primitives,
 * Map of primitive values, etc., then you can use {@link ProjectRootManager} as a cache dependency.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Experimental
public final class JavaLibraryModificationTracker implements ModificationTracker, Disposable {
  private final ModificationTracker myProjectRootManager;
  private final ModificationTracker myDumbServiceModificationTracker;
  private final ModificationTracker myForcefulReparseModificationTracker;  // PsiClass from libraries may become invalid on reparse
  private final SimpleModificationTracker myOnContentReloadModificationTracker = new SimpleModificationTracker();

  public JavaLibraryModificationTracker(Project project) {
    myProjectRootManager = ProjectRootManager.getInstance(project);
    myDumbServiceModificationTracker = DumbService.getInstance(project).getModificationTracker();
    myForcefulReparseModificationTracker = ForcefulReparseModificationTracker.getInstance();

    MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerListener() {
      private final GlobalSearchScope projectLibraryScope = ProjectScope.getLibrariesScope(project);

      @Override
      public void fileWithNoDocumentChanged(@NotNull VirtualFile file) {
        if (projectLibraryScope.contains(file)) {
          myOnContentReloadModificationTracker.incModificationCount();
        }
      }
    });
  }

  @Override
  public long getModificationCount() {
    return myProjectRootManager.getModificationCount()
           + myDumbServiceModificationTracker.getModificationCount()
           + myForcefulReparseModificationTracker.getModificationCount()
           + myOnContentReloadModificationTracker.getModificationCount();
  }

  @TestOnly
  public void incModificationCount() {
    myOnContentReloadModificationTracker.incModificationCount();
  }

  @Override
  public void dispose() {
  }

  public static ModificationTracker getInstance(Project project) {
    return project.getService(JavaLibraryModificationTracker.class);
  }

  @TestOnly
  public static void incModificationCount(Project project) {
    project.getService(JavaLibraryModificationTracker.class).incModificationCount();
  }
}
