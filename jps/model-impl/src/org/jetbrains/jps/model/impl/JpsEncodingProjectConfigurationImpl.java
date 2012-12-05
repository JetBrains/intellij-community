package org.jetbrains.jps.model.impl;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsEncodingConfigurationService;
import org.jetbrains.jps.model.JpsEncodingProjectConfiguration;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class JpsEncodingProjectConfigurationImpl extends JpsElementBase<JpsEncodingProjectConfigurationImpl>
  implements JpsEncodingProjectConfiguration {
  public static final JpsElementChildRole<JpsEncodingProjectConfiguration> ROLE = JpsElementChildRoleBase.create("encoding configuration");
  private final Map<String, String> myUrlToEncoding = new HashMap<String, String>();
  private final String myProjectEncoding;

  public JpsEncodingProjectConfigurationImpl(Map<String, String> urlToEncoding, String projectEncoding) {
    myUrlToEncoding.putAll(urlToEncoding);
    myProjectEncoding = projectEncoding;
  }

  @Nullable
  @Override
  public String getEncoding(@NotNull File file) {
    if (!myUrlToEncoding.isEmpty()) {

      File current = file;
      while (current != null) {
        String encoding = myUrlToEncoding.get(JpsPathUtil.pathToUrl(FileUtilRt.toSystemIndependentName(current.getPath())));

        if (encoding != null) {
          return encoding;
        }

        current = FileUtilRt.getParentFile(current);
      }
    }

    if (myProjectEncoding != null) {
      return myProjectEncoding;
    }

    final JpsModel model = getModel();
    assert model != null;
    return JpsEncodingConfigurationService.getInstance().getGlobalEncoding(model.getGlobal());
  }

  @NotNull
  @Override
  public Map<String, String> getUrlToEncoding() {
    return Collections.unmodifiableMap(myUrlToEncoding);
  }

  @Nullable
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
