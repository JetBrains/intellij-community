package com.intellij.openapi.components;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface StorageAnnotationsDefaultValues {
  class NullStateStorage implements StateStorage {
    @Nullable
    public <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto)
    throws StateStorageException {
      throw new UnsupportedOperationException("Method getState is not supported in " + getClass());
    }

    public void setState(Object component, final String componentName, Object state) throws StateStorageException {
      throw new UnsupportedOperationException("Method setState is not supported in " + getClass());
    }

    public List<VirtualFile> getAllStorageFiles() {
      throw new UnsupportedOperationException("Method getAllStorageFiles is not supported in " + getClass());
    }

    public boolean needsSave() throws StateStorageException {
      throw new UnsupportedOperationException("Method needsSave is not supported in " + getClass());
    }

    public void save() throws StateStorageException {
      throw new UnsupportedOperationException("Method save is not supported in " + getClass());
    }
  }

  class NullStateStorageChooser implements StateStorageChooser {
    public Storage selectStorage(Storage[] storages, Object component, final Operation operation) {
      throw new UnsupportedOperationException("Method selectStorage is not supported in " + getClass());
    }
  }
}
