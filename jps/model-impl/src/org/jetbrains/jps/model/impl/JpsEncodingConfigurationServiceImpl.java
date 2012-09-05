package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;

import java.util.Map;

/**
 * @author nik
 */
public class JpsEncodingConfigurationServiceImpl extends JpsEncodingConfigurationService {
  private static final JpsElementChildRoleBase<JpsSimpleElement<String>> ENCODING_ROLE = JpsElementChildRoleBase.create("encoding");

  @Nullable
  @Override
  public String getGlobalEncoding(@NotNull JpsGlobal global) {
    JpsSimpleElement<String> encoding = global.getContainer().getChild(ENCODING_ROLE);
    return encoding != null ? encoding.getData() : null;
  }

  @Override
  public void setGlobalEncoding(@NotNull JpsGlobal global, @Nullable String encoding) {
    global.getContainer().setChild(ENCODING_ROLE, JpsElementFactory.getInstance().createSimpleElement(encoding));
  }

  @Nullable
  @Override
  public String getProjectEncoding(@NotNull JpsModel model) {
    JpsEncodingProjectConfiguration configuration = getEncodingConfiguration(model.getProject());
    if (configuration != null) {
      String projectEncoding = configuration.getProjectEncoding();
      if (projectEncoding != null) {
        return projectEncoding;
      }
    }
    return getGlobalEncoding(model.getGlobal());
  }

  @Nullable
  @Override
  public JpsEncodingProjectConfiguration getEncodingConfiguration(@NotNull JpsProject project) {
    return project.getContainer().getChild(JpsEncodingProjectConfigurationImpl.ROLE);
  }

  @NotNull
  @Override
  public JpsEncodingProjectConfiguration setEncodingConfiguration(@NotNull JpsProject project,
                                                                  @Nullable String projectEncoding,
                                                                  @NotNull Map<String, String> urlToEncoding) {
    JpsEncodingProjectConfigurationImpl configuration = new JpsEncodingProjectConfigurationImpl(urlToEncoding, projectEncoding);
    return project.getContainer().setChild(JpsEncodingProjectConfigurationImpl.ROLE, configuration);
  }
}
