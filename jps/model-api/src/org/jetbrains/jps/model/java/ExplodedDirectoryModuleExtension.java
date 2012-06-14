package org.jetbrains.jps.model.java;

import org.jetbrains.jps.model.JpsElement;

/**
 * @author nik
 */
//todo[nik] move to j2me plugin
public interface ExplodedDirectoryModuleExtension extends JpsElement {

  String getExplodedUrl();

  void setExplodedUrl(String explodedUrl);

  boolean isExcludeExploded();

  void setExcludeExploded(boolean excludeExploded);
}
