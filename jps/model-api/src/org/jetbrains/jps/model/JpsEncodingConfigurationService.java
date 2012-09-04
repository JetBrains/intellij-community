package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.Map;

/**
 * @author nik
 */
public abstract class JpsEncodingConfigurationService {
  public static JpsEncodingConfigurationService getInstance() {
    return JpsServiceManager.getInstance().getService(JpsEncodingConfigurationService.class);
  }

  @Nullable
  public abstract String getGlobalEncoding(@NotNull JpsGlobal global);

  public abstract void setGlobalEncoding(@NotNull JpsGlobal global, @Nullable String encoding);

  @Nullable
  public abstract String getProjectEncoding(@NotNull JpsModel model);

  @Nullable
  public abstract JpsEncodingProjectConfiguration getEncodingConfiguration(@NotNull JpsProject project);

  @NotNull
  public abstract JpsEncodingProjectConfiguration setEncodingConfiguration(@NotNull JpsProject project, @Nullable String projectEncoding,
                                                                           @NotNull Map<String, String> urlToEncoding);
}
