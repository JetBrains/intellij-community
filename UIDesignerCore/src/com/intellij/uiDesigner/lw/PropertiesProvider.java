package com.intellij.uiDesigner.lw;

import java.util.HashMap;   // [stathik] moved back

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public interface PropertiesProvider {
  /**
   * @return key - property name (String), value - LwProperty. If class cannot be inspected for some reason,
   * returns null 
   */ 
  HashMap getLwProperties(String className);
}
