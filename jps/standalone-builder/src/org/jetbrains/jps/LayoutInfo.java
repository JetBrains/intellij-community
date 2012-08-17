package org.jetbrains.jps;

import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
//todo[nik] inline this class later
public class LayoutInfo {
  private Set<String> myUsedModules = new HashSet<String>();

  public Set<String> getUsedModules() {
    return myUsedModules;
  }
}
