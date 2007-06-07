package com.intellij.openapi.components;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public interface StorageAnnotationsDefaultValues {
  class NullStateStorage implements StateStorage {
    @Nullable
    public <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto)
    throws StateStorageException {
      throw new UnsupportedOperationException("Method getState is not supported in " + getClass());
    }

    public boolean hasState(final Object component, final String componentName, final Class<?> aClass) throws StateStorageException {
      throw new UnsupportedOperationException("Method hasState not implemented in " + getClass());
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

    public Set<String> getUsedMacros() {
      throw new UnsupportedOperationException("Method getUsedMacros not implemented in " + getClass());
    }

    @NotNull
    public ExternalizationSession startExternalization() {
      throw new UnsupportedOperationException("Method startExternalization not implemented in " + getClass());
    }

    @NotNull
    public SaveSession startSave(ExternalizationSession externalizationSession) {
      throw new UnsupportedOperationException("Method startSave not implemented in " + getClass());
    }

    public void finishSave(SaveSession saveSession) {
      throw new UnsupportedOperationException("Method finishSave not implemented in " + getClass());
    }

    public void reload(final Set<String> changedComponents) throws StateStorageException {
      throw new UnsupportedOperationException("Method reload not implemented in " + getClass());
    }
  }

  class NullStateStorageChooser implements StateStorageChooser {
    public Storage[] selectStorages(Storage[] storages, Object component, final StateStorageOperation operation) {
      throw new UnsupportedOperationException("Method selectStorages is not supported in " + getClass());
    }
  }

  class NullStateSplitter implements StateSplitter {
    public List<Pair<Element, String>> splitState(Element e) {
      throw new UnsupportedOperationException("Method splitState not implemented in " + getClass());
    }

    public void mergeStatesInto(final Element target, final Element[] elements) {
      throw new UnsupportedOperationException("Method mergeStatesInto not implemented in " + getClass());
    }
  }
}
