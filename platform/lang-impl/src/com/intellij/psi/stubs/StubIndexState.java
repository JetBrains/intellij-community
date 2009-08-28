/*
 * @author max
 */
package com.intellij.psi.stubs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class StubIndexState {
  public List<String> registeredIndices = new ArrayList<String>();

  public StubIndexState() {
  }

  public StubIndexState(Collection<StubIndexKey<?, ?>> keys) {
    for (StubIndexKey key : keys) {
      registeredIndices.add(key.toString());
    }
  }
}