package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StateStorage;
import com.intellij.util.io.fs.IFile;

import java.util.*;

/**
 * @author mike
 */
public class CompoundSaveSession {
  private Map<StateStorage, StateStorage.SaveSession> mySaveSessions = new HashMap<StateStorage, StateStorage.SaveSession>();

  public CompoundSaveSession(final CompoundExternalizationSession compoundExternalizationSession) {
    final Collection<StateStorage> stateStorages = compoundExternalizationSession.getStateStorages();

    for (StateStorage stateStorage : stateStorages) {
      mySaveSessions.put(stateStorage, stateStorage.startSave(compoundExternalizationSession.getExternalizationSession(stateStorage)));
    }
  }

  public List<IFile> getAllStorageFilesToSave() throws StateStorage.StateStorageException {
    List<IFile> result = new ArrayList<IFile>();

    for (StateStorage stateStorage : mySaveSessions.keySet()) {
      final StateStorage.SaveSession saveSession = mySaveSessions.get(stateStorage);

      result.addAll(saveSession.getStorageFilesToSave());
    }

    return result;
  }

  public void save() throws StateStorage.StateStorageException {
    for (StateStorage.SaveSession saveSession : mySaveSessions.values()) {
      saveSession.save();
    }
  }

  public Set<String> getUsedMacros() {
    Set<String> usedMacros = new HashSet<String>();

    for (StateStorage.SaveSession saveSession : mySaveSessions.values()) {
      usedMacros.addAll(saveSession.getUsedMacros());
    }

    return usedMacros;
  }

  public void finishSave() {
    for (StateStorage stateStorage : mySaveSessions.keySet()) {
      final StateStorage.SaveSession saveSession = mySaveSessions.get(stateStorage);

      stateStorage.finishSave(saveSession);
    }
  }

  public StateStorage.SaveSession getSaveSession(final StateStorage storage) {
    return mySaveSessions.get(storage);
  }

  public List<IFile> getAllStorageFiles() {
    List<IFile> result = new ArrayList<IFile>();
    for (StateStorage.SaveSession saveSession : mySaveSessions.values()) {
      result.addAll(saveSession.getAllStorageFiles());
    }

    return result;
  }

  public Set<StateStorage> getStateStorages() {
    return mySaveSessions.keySet();
  }
}
