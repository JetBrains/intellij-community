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
package com.intellij.core.indexing;

import com.intellij.ide.caches.FileContent;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.stubs.SerializationManager;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.*;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;


public class FileBasedIndexJavaComponent extends FileBasedIndex implements BaseComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.core.indexing.FileBasedIndexJavaComponent");

  public FileBasedIndexJavaComponent(MessageBus bus,
                                     FileBasedIndexUnsavedDocumentsManager unsavedDocumentsManager,
                                     FileBasedIndexIndicesManager indexIndicesManager,
                                     FileBasedIndexTransactionMap transactionMap,
                                     FileBasedIndexLimitsChecker limitsChecker,
                                     AbstractVfsAdapter vfsAdapter,
                                     IndexingStamp indexingStamp,
                                     SerializationManager sm) throws IOException {
    super(bus, unsavedDocumentsManager, indexIndicesManager, transactionMap, limitsChecker, vfsAdapter, indexingStamp, sm);
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  public void iterateIndexableFiles(ContentIterator processor, Project project, ProgressIndicator indicator) {
    if (project.isDisposed()) {
      return;
    }
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    // iterate project content
    projectFileIndex.iterateContent(processor);

    if (project.isDisposed()) {
      return;
    }

    Set<VirtualFile> visitedRoots = new HashSet<VirtualFile>();
    // iterate associated libraries
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (module.isDisposed()) {
        return;
      }
      OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry) {
          if (orderEntry.isValid()) {
            final VirtualFile[] libSources = orderEntry.getFiles(OrderRootType.SOURCES);
            final VirtualFile[] libClasses = orderEntry.getFiles(OrderRootType.CLASSES);
            for (VirtualFile[] roots : new VirtualFile[][]{libSources, libClasses}) {
              for (VirtualFile root : roots) {
                if (visitedRoots.add(root)) {
                  iterateRecursively(root, processor, indicator);
                }
              }
            }
          }
        }
      }
    }
  }

  private static void iterateRecursively(@Nullable final VirtualFile root, final ContentIterator processor, ProgressIndicator indicator) {
    if (root != null) {
      if (indicator != null) {
        indicator.checkCanceled();
        indicator.setText2(root.getPresentableUrl());
      }

      if (root.isDirectory()) {
        for (VirtualFile file : root.getChildren()) {
          if (file.isDirectory()) {
            iterateRecursively(file, processor, indicator);
          }
          else {
            processor.processFile(file);
          }
        }
      }
      else {
        processor.processFile(root);
      }
    }
  }

  @Override
  protected void handleDumbMode(@Nullable Project project) {
  }

  @Override
  protected boolean isDumb(@Nullable Project project) {
    return false;
  }

  @Override
  protected void scheduleIndexRebuild(boolean forceDumbMode) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      UnindexedFilesUpdater updater = new UnindexedFilesUpdater(project, this, getIndexingStamp());
      VirtualFile[] virtualFiles = updater.queryNeededFiles(new EmptyProgressIndicator());

      for (VirtualFile f : virtualFiles)
      {
        FileContent content = new FileContent(f);

        if (f.isValid() && !f.isDirectory()) {
          if (!doLoadContent(content)) {
            content.setEmptyContent();
          }
        }
        else {
          content.setEmptyContent();
        }

        updater.processFile(content);
      }
    }
  }

  private static boolean doLoadContent(final FileContent content) {
    try {
      content.getBytes(); // Reads the content bytes and caches them.
      return true;
    }
    catch (Throwable e) {
      LOG.error(e);
      return false;
    }
  }


  @Override
  protected Project guessProjectFile(VirtualFile file) {
    return ProjectUtil.guessProjectForFile(file);
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "com.intellij.core.indexing.FileBasedIndexJavaComponent";
  }
}
