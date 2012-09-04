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
package com.intellij.openapi.roots.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.messages.MessageBusConnection;

import java.util.ArrayList;
import java.util.List;

/**
 * Descendant of DirectoryIndex which can update itself automatically based on VFS changes.
 */
public class DirectoryIndexComponent extends DirectoryIndexImpl {
  private final MessageBusConnection myConnection;

  public DirectoryIndexComponent(Project project, StartupManager startupManager) {
    super(project);
    myConnection = project.getMessageBus().connect(project);
    startupManager.registerPreStartupActivity(new Runnable() {
      @Override
      public void run() {
        initialize();
      }
    });
  }

  @Override
  public void initialize() {
    subscribeToFileChanges();
    super.initialize();
    markContentRootsForRefresh();
  }

  private void subscribeToFileChanges() {
    myConnection.subscribe(FileTypeManager.TOPIC, new FileTypeListener.Adapter() {
      @Override
      public void fileTypesChanged(FileTypeEvent event) {
        doInitialize();
      }
    });

    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        doInitialize();
      }
    });

    myConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(new MyVirtualFileListener()));
  }

  private void markContentRootsForRefresh() {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      for (VirtualFile contentRoot : contentRoots) {
        if (contentRoot instanceof NewVirtualFile) {
          ((NewVirtualFile)contentRoot).markDirtyRecursively();
        }
      }
    }
  }

  @Override
  protected void dispatchPendingEvents() {
    myConnection.deliverImmediately();
  }

  private class MyVirtualFileListener extends VirtualFileAdapter {
    private final Key<List<VirtualFile>> FILES_TO_RELEASE_KEY = Key.create("DirectoryIndexImpl.MyVirtualFileListener.FILES_TO_RELEASE_KEY");

    @Override
    public void fileCreated(VirtualFileEvent event) {
      VirtualFile file = event.getFile();

      if (!file.isDirectory()) return;

      VirtualFile parent = file.getParent();
      if (parent == null) return;

      myState = updateStateWithNewFile(file, parent);
    }

    private IndexState updateStateWithNewFile(VirtualFile file, VirtualFile parent) {
      final IndexState originalState = myState;
      IndexState state = originalState;
      DirectoryInfo parentInfo = originalState.myDirToInfoMap.get(parent);

      // fill info for all nested roots
      for (Module eachModule : ModuleManager.getInstance(myProject).getModules()) {
        for (ContentEntry eachRoot : getContentEntries(eachModule)) {
          if (parentInfo != null && eachRoot == parentInfo.contentRoot) continue;

          if (FileUtil.startsWith(eachRoot.getUrl(), file.getUrl())) {
            String rel = FileUtil.getRelativePath(file.getUrl(), eachRoot.getUrl(), '/');
            if (rel != null) {
              VirtualFile f = file.findFileByRelativePath(rel);
              if (f != null) {
                if (state == originalState) state = state.copy();
                state.fillMapWithModuleContent(f, eachModule, f, null);
              }
            }
          }
        }
      }

      if (parentInfo == null) return state;

      Module module = parentInfo.module;

      for (DirectoryIndexExcludePolicy policy : myExcludePolicies) {
        if (policy.isExcludeRoot(file)) return state;
      }

      if (state == originalState) state = state.copy();
      state.fillMapWithModuleContent(file, module, parentInfo.contentRoot, null);

      String parentPackage = state.myDirToPackageName.get(parent);

      if (module != null) {
        if (parentInfo.isInModuleSource) {
          String newDirPackageName = getPackageNameForSubdir(parentPackage, file.getName());
          state.fillMapWithModuleSource(file, module, newDirPackageName, parentInfo.sourceRoot, parentInfo.isTestSource, null);
        }
      }

      if (parentInfo.libraryClassRoot != null) {
        String newDirPackageName = getPackageNameForSubdir(parentPackage, file.getName());
        state.fillMapWithLibraryClasses(file, newDirPackageName, parentInfo.libraryClassRoot, null);
      }

      if (parentInfo.isInLibrarySource) {
        String newDirPackageName = getPackageNameForSubdir(parentPackage, file.getName());
        state.fillMapWithLibrarySources(file, newDirPackageName, parentInfo.sourceRoot, null);
      }

      if (!parentInfo.getOrderEntries().isEmpty()) {
        state.fillMapWithOrderEntries(file, parentInfo.getOrderEntries(), null, null, null, parentInfo, null);
      }
      return state;
    }

    @Override
    public void beforeFileDeletion(VirtualFileEvent event) {
      VirtualFile file = event.getFile();
      if (!file.isDirectory()) return;
      if (!myState.myDirToInfoMap.containsKey(file)) return;

      final IndexState state = myState.copy();

      ArrayList<VirtualFile> list = new ArrayList<VirtualFile>();
      addDirsRecursively(state, list, file);
      file.putUserData(FILES_TO_RELEASE_KEY, list);
      myState = state;
    }

    private void addDirsRecursively(IndexState state, ArrayList<VirtualFile> list, VirtualFile dir) {
      if (!state.myDirToInfoMap.containsKey(dir) || !(dir instanceof NewVirtualFile)) return;

      list.add(dir);

      for (VirtualFile child : ((NewVirtualFile)dir).getCachedChildren()) {
        if (child.isDirectory()) {
          addDirsRecursively(state, list, child);
        }
      }
    }

    @Override
    public void fileDeleted(VirtualFileEvent event) {
      VirtualFile file = event.getFile();
      List<VirtualFile> list = file.getUserData(FILES_TO_RELEASE_KEY);
      if (list == null) return;

      IndexState copy = null;
      for (VirtualFile dir : list) {
        if (myState.myDirToInfoMap.containsKey(dir)) {
          if (copy == null) copy = myState.copy();

          copy.myDirToInfoMap.remove(dir);
          copy.setPackageName(dir, null);
        }
      }

      if (copy != null) {
        myState = copy;
      }
    }

    @Override
    public void fileMoved(VirtualFileMoveEvent event) {
      VirtualFile file = event.getFile();
      if (file.isDirectory()) {
        doInitialize();
      }
    }

    @Override
    public void propertyChanged(VirtualFilePropertyEvent event) {
      if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
        VirtualFile file = event.getFile();
        if (file.isDirectory()) {
          doInitialize();
        }
      }
    }
  }

}
