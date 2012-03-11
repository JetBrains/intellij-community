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

/*
 * @author max
 */

package com.intellij.util.indexing;

import com.intellij.history.LocalHistory;
import com.intellij.ide.caches.CacheUpdater;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiManager;
import com.intellij.psi.stubs.SerializationManager;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FileBasedIndexComponent extends FileBasedIndex implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.FileBasedIndexComponent");
  
  public FileBasedIndexComponent(final VirtualFileManagerEx vfManager,
                                 FileDocumentManager fdm,
                                 FileTypeManager fileTypeManager,
                                 MessageBus bus,
                                 FileBasedIndexUnsavedDocumentsManager unsavedDocumentsManager,
                                 FileBasedIndexIndicesManager indexIndicesManager,
                                 FileBasedIndexTransactionMap transactionMap,
                                 FileBasedIndexLimitsChecker limitsChecker,
                                 AbstractVfsAdapter vfsAdapter,
                                 IndexingStamp indexingStamp,
                                 SerializationManager sm) throws IOException {
    super(vfManager, fdm, bus, unsavedDocumentsManager, indexIndicesManager, transactionMap, limitsChecker, vfsAdapter, indexingStamp, sm);

    final MessageBusConnection connection = bus.connect();
    final FileTypeManager fileTypeManager_ = fileTypeManager;
    final FileBasedIndexIndicesManager indexIndicesManager_ = indexIndicesManager;
    
    connection.subscribe(FileTypeManager.TOPIC, new FileTypeListener() {
      private Map<FileType, Set<String>> myTypeToExtensionMap;
      @Override
      public void beforeFileTypesChanged(final FileTypeEvent event) {
        cleanupProcessedFlag(null);
        myTypeToExtensionMap = new HashMap<FileType, Set<String>>();
        for (FileType type : fileTypeManager_.getRegisteredFileTypes()) {
          myTypeToExtensionMap.put(type, getExtensions(type));
        }
      }

      @Override
      public void fileTypesChanged(final FileTypeEvent event) {
        final Map<FileType, Set<String>> oldExtensions = myTypeToExtensionMap;
        myTypeToExtensionMap = null;
        if (oldExtensions != null) {
          final Map<FileType, Set<String>> newExtensions = new HashMap<FileType, Set<String>>();
          for (FileType type : fileTypeManager_.getRegisteredFileTypes()) {
            newExtensions.put(type, getExtensions(type));
          }
          // we are interested only in extension changes or removals.
          // addition of an extension is handled separately by RootsChanged event
          if (!newExtensions.keySet().containsAll(oldExtensions.keySet())) {
            rebuildAllIndices();
            return;
          }
          for (Map.Entry<FileType, Set<String>> entry : oldExtensions.entrySet()) {
            FileType fileType = entry.getKey();
            Set<String> strings = entry.getValue();
            if (!newExtensions.get(fileType).containsAll(strings)) {
              rebuildAllIndices();
              return;
            }
          }
        }
      }

      private Set<String> getExtensions(FileType type) {
        final Set<String> set = new HashSet<String>();
        for (FileNameMatcher matcher : fileTypeManager_.getAssociations(type)) {
          set.add(matcher.getPresentableString());
        }
        return set;
      }

      private void rebuildAllIndices() {
        for (ID<?, ?> indexId : indexIndicesManager_.keySet()) {
          try {
            clearIndex(indexId);
          }
          catch (StorageException e) {
            LOG.info(e);
          }
        }
        scheduleIndexRebuild(true);
      }
    });


    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void before(List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          final Object requestor = event.getRequestor();
          if (requestor instanceof FileDocumentManager || requestor instanceof PsiManager || requestor == LocalHistory.VFS_EVENT_REQUESTOR) {
            cleanupMemoryStorage();
            break;
          }
        }
      }

      @Override
      public void after(List<? extends VFileEvent> events) {
      }
    });
  }

  protected void notifyIndexRebuild(String rebuildNotification) {
    if(!ApplicationManager.getApplication().isHeadlessEnvironment())
      new NotificationGroup("Indexing", NotificationDisplayType.BALLOON, false)
        .createNotification("Index Rebuild", rebuildNotification, NotificationType.INFORMATION, null).notify(null);
  }

  @Override
  protected boolean isDumb(@Nullable Project project) {
    if (project != null) {
      return DumbServiceImpl.getInstance(project).isDumb();
    }
    for (Project proj : ProjectManager.getInstance().getOpenProjects()) {
      if (DumbServiceImpl.getInstance(proj).isDumb()) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void scheduleIndexRebuild(boolean forceDumbMode) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      final Set<CacheUpdater> updatersToRun = Collections.<CacheUpdater>singleton(new UnindexedFilesUpdater(project, this, getIndexingStamp()));
      final DumbServiceImpl service = DumbServiceImpl.getInstance(project);
      if (forceDumbMode) {
        service.queueCacheUpdateInDumbMode(updatersToRun);
      }
      else {
        service.queueCacheUpdate(updatersToRun);
      }
    }
  }

  @Override
  protected Project guessProjectFile(VirtualFile file) {
    return ProjectUtil.guessProjectForFile(file);
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "com.intellij.util.indexing.FileBasedIndexComponent";
  }

  public static void iterateIndexableFiles(final ContentIterator processor, Project project, ProgressIndicator indicator) {
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
    for (IndexedRootsProvider provider : Extensions.getExtensions(IndexedRootsProvider.EP_NAME)) {
      //important not to depend on project here, to support per-project background reindex
      // each client gives a project to FileBasedIndex
      if (project.isDisposed()) {
        return;
      }
      for (VirtualFile root : IndexableSetContributor.getRootsToIndex(provider)) {
        if (visitedRoots.add(root)) {
          iterateRecursively(root, processor, indicator);
        }
      }
      for (VirtualFile root : IndexableSetContributor.getProjectRootsToIndex(provider, project)) {
        if (visitedRoots.add(root)) {
          iterateRecursively(root, processor, indicator);
        }
      }
    }

    if (project.isDisposed()) {
      return;
    }
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
}
