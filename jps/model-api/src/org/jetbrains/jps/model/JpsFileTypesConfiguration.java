package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface JpsFileTypesConfiguration extends JpsElement {
  String getIgnoredPatternString();

  void setIgnoredPatternString(@NotNull String ignoredPatternString);
}
