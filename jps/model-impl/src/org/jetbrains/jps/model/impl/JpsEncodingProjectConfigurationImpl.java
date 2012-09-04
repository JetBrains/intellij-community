package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsEncodingProjectConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class JpsEncodingProjectConfigurationImpl extends JpsElementBase<JpsEncodingProjectConfigurationImpl> implements JpsEncodingProjectConfiguration {
  public static final JpsElementChildRole<JpsEncodingProjectConfiguration> ROLE = JpsElementChildRoleBase.create("encoding configuration");
  private final Map<String, String> myUrlToEncoding = new HashMap<String, String>();
  private final String myProjectEncoding;

  public JpsEncodingProjectConfigurationImpl(Map<String, String> urlToEncoding, String projectEncoding) {
    myUrlToEncoding.putAll(urlToEncoding);
    myProjectEncoding = projectEncoding;
  }

  @Override
  public String getEncoding(@NotNull String url) {
    return myUrlToEncoding.get(url);
  }

  @NotNull
  @Override
  public Map<String, String> getUrlToEncoding() {
    return Collections.unmodifiableMap(myUrlToEncoding);
  }

  @Override
  public String getProjectEncoding() {
    return myProjectEncoding;
  }

  @NotNull
  @Override
  public JpsEncodingProjectConfigurationImpl createCopy() {
    return new JpsEncodingProjectConfigurationImpl(myUrlToEncoding, myProjectEncoding);
  }

  @Override
  public void applyChanges(@NotNull JpsEncodingProjectConfigurationImpl modified) {
  }
}
