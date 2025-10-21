// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
final class DumpVfsInfoForExcludedFilesAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    Set<String> excludeRoots = new HashSet<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      Collections.addAll(excludeRoots, ModuleRootManager.getInstance(module).getExcludeRootUrls());
    }
    for (DirectoryIndexExcludePolicy policy : DirectoryIndexExcludePolicy.EP_NAME.getExtensions(project)) {
      ContainerUtil.addAll(excludeRoots, policy.getExcludeUrlsForProject());
    }

    if (excludeRoots.isEmpty()) {
      System.out.println("No excluded roots found in project.");
    }

    for (String root : excludeRoots) {
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(root);
      if (file == null) {
        System.out.println(root + " not in VFS");
        continue;
      }
      dumpChildrenInDbRecursively(file, 0);
    }
  }

  private static void dumpChildrenInDbRecursively(VirtualFile dir, int depth) {
    if (!(dir instanceof NewVirtualFile)) {
      System.out.println(dir.getPresentableUrl() + ": not in db (" + dir.getClass().getName() + ")");
      return;
    }

    List<VirtualFile> dirs = new ArrayList<>();
    int inDb = 0, contentInDb = 0, nullChildren = 0;
    PersistentFS persistentFS = PersistentFS.getInstance();
    if (persistentFS.wereChildrenAccessed(dir)) {
      for (String name : persistentFS.listPersisted(dir)) {
        inDb++;
        NewVirtualFile child = ((NewVirtualFile)dir).refreshAndFindChild(name);
        if (child == null) {
          nullChildren++;
          continue;
        }
        if (child.isDirectory()) {
          dirs.add(child);
        }
        else if (persistentFS.getCurrentContentId(child) != 0) {
          contentInDb++;
        }
      }
    }
    System.out.print(dir.getPresentableUrl() + ": " + inDb + " children in db");
    if (contentInDb > 0) {
      System.out.print(", content of " + contentInDb + " files in db");
    }
    if (nullChildren > 0) {
      System.out.print(", " + nullChildren + " invalid files in db");
    }
    System.out.println();

    if (depth > 10) {
      System.out.println("too deep, skipping children");
    }
    else {
      for (VirtualFile childDir : dirs) {
        dumpChildrenInDbRecursively(childDir, depth+1);
      }
    }
  }
}
