package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StateStorage;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author mike
 */
public class CompoundExternalizationSession {
  private Map<StateStorage, StateStorage.ExternalizationSession> mySessions = new HashMap<StateStorage, StateStorage.ExternalizationSession>();

  public StateStorage.ExternalizationSession getExternalizationSession(StateStorage stateStore) {
    StateStorage.ExternalizationSession session = mySessions.get(stateStore);
    if (session == null) {
      mySessions.put(stateStore, session = stateStore.startExternalization());
    }

    return session;
  }


  public Collection<StateStorage> getStateStorages() {
    return mySessions.keySet();
  }
}
