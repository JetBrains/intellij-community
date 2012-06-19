package org.jetbrains.jps.model.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;

/**
 * @author nik
 */
public interface JpsLibraryRoot extends JpsElement {
  @NotNull
  JpsOrderRootType getRootType();

  @NotNull
  String getUrl();

  @NotNull
  InclusionOptions getInclusionOptions();

  @NotNull
  JpsLibrary getLibrary();

  enum InclusionOptions {ROOT_ITSELF, ARCHIVES_UNDER_ROOT, ARCHIVES_UNDER_ROOT_RECURSIVELY}
}
