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

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.psi.stubs.SerializationManager;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
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
                                 SerializationManager sm) throws IOException {
    super(vfManager, fdm, bus, unsavedDocumentsManager, indexIndicesManager, transactionMap, limitsChecker, sm);

    final MessageBusConnection connection = bus.connect();
    final FileTypeManager fileTypeManager_ = fileTypeManager;
    final FileBasedIndexIndicesManager indexIndicesManager_ = indexIndicesManager;
    
    connection.subscribe(FileTypeManager.TOPIC, new FileTypeListener() {
      private Map<FileType, Set<String>> myTypeToExtensionMap;
      @Override
      public void beforeFileTypesChanged(final FileTypeEvent event) {
        cleanupProcessedFlag();
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
  }

  protected void notifyIndexRebuild(String rebuildNotification) {
    if(!ApplicationManager.getApplication().isHeadlessEnvironment())
      new NotificationGroup("Indexing", NotificationDisplayType.BALLOON, false)
        .createNotification("Index Rebuild", rebuildNotification, NotificationType.INFORMATION, null).notify(null);
  }
}
