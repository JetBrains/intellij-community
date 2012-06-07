package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public interface JpsUrlList extends JpsElement {
  @NotNull
  List<String> getUrls();

  void addUrl(@NotNull String url);

  void removeUrl(@NotNull String url);
}
