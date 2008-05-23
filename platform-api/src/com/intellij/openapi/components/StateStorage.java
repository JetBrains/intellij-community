/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.openapi.components;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.util.io.fs.IFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface StateStorage {
  Topic<Listener> STORAGE_TOPIC = new Topic<Listener>("STORAGE_LISTENER", Listener.class, Topic.BroadcastDirection.TO_PARENT);

  @Nullable
  <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto) throws StateStorageException;
  boolean hasState(final Object component, final String componentName, final Class<?> aClass) throws StateStorageException;

  @NotNull
  ExternalizationSession startExternalization();
  @NotNull
  SaveSession startSave(ExternalizationSession externalizationSession);
  void finishSave(SaveSession saveSession);

  void reload(final Set<String> changedComponents) throws StateStorageException;

  interface ExternalizationSession {
    void setState(Object component, final String componentName, Object state, @Nullable final Storage storageSpec) throws StateStorageException;
  }

  interface SaveSession {
    void save() throws StateStorageException;

    Set<String> getUsedMacros();

    @Nullable
    Set<String> analyzeExternalChanges(final Set<Pair<VirtualFile,StateStorage>> changedFiles);

    Collection<IFile> getStorageFilesToSave() throws StateStorageException;
    List<IFile> getAllStorageFiles();
  }

  class StateStorageException extends RuntimeException {
    public StateStorageException() {
    }

    public StateStorageException(final String message) {
      super(message);
    }

    public StateStorageException(final String message, final Throwable cause) {
      super(message, cause);
    }

    public StateStorageException(final Throwable cause) {
      super(cause);
    }
  }

  interface Listener {
    void storageFileChanged(final VirtualFileEvent event, final StateStorage storage);
  }
}
