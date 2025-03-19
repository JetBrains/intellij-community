// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.psi.PsiJavaModule;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Service(Service.Level.PROJECT)
public final class PsiJavaModuleModificationTracker extends SimpleModificationTracker implements Disposable {

  private final @NotNull Project myProject;

  public static PsiJavaModuleModificationTracker getInstance(Project project) {
    return project.getService(PsiJavaModuleModificationTracker.class);
  }

  @Override
  public long getModificationCount() {
    return super.getModificationCount() + DumbService.getInstance(myProject).getModificationTracker().getModificationCount();
  }

  public PsiJavaModuleModificationTracker(Project project) {
    myProject = project;
    MessageBusConnection connect = project.getMessageBus().connect(this);
    connect.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        for (VFileEvent event : events) {
          VirtualFile file = event.getFile();
          if (file != null) {
            if (!file.isDirectory() && isModuleFile(file.getName()) ||
                event instanceof VFileDeleteEvent || //ensure inc when directory with MANIFEST.MF was deleted
                event instanceof VFilePropertyChangeEvent &&
                //ensure inc when directory with MANIFEST.MF was renamed or manifest was renamed to a new name
                VirtualFile.PROP_NAME.equals(((VFilePropertyChangeEvent)event).getPropertyName())) {
              incModificationCount();
              break;
            }
          }
        }
      }
    });
  }

  static boolean isModuleFile(String name) {
    return PsiJavaModule.MODULE_INFO_FILE.equals(name) || "MANIFEST.MF".equalsIgnoreCase(name);
  }

  @Override
  public void dispose() { }
}
