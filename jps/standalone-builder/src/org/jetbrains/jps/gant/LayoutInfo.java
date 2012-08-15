package org.jetbrains.jps.gant;

import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class LayoutInfo {
  private Set<String> myUsedModules = new HashSet<String>();

  public Set<String> getUsedModules() {
    return myUsedModules;
  }
}
