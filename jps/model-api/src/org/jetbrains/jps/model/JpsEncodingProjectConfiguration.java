package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author nik
 */
public interface JpsEncodingProjectConfiguration extends JpsElement {
  @Nullable
  String getEncoding(@NotNull String url);

  @Nullable
  String getProjectEncoding();

  @NotNull
  Map<String, String> getUrlToEncoding();
}
