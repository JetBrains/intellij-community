// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.text.XmlCharsetDetector;
import org.jetbrains.annotations.ApiStatus;
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
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@ApiStatus.Internal
public final class JpsEncodingProjectConfigurationImpl extends JpsElementBase<JpsEncodingProjectConfigurationImpl>
  implements JpsEncodingProjectConfiguration {
  private static final Logger LOG = Logger.getInstance(JpsEncodingProjectConfigurationImpl.class);
  public static final JpsElementChildRole<JpsEncodingProjectConfiguration> ROLE = JpsElementChildRoleBase.create("encoding configuration");
  private static final String XML_NAME_SUFFIX = ".xml";
  private final Map<String, String> myUrlToEncoding = new HashMap<>();
  private final String myProjectEncoding;

  public JpsEncodingProjectConfigurationImpl(Map<String, String> urlToEncoding, String projectEncoding) {
    myUrlToEncoding.putAll(urlToEncoding);
    myProjectEncoding = projectEncoding;
  }

  @Override
  public @Nullable String getEncoding(@NotNull File file) {
    if (isXmlFile(file)) {
      try {
        String encoding = XmlCharsetDetector.extractXmlEncodingFromProlog(FileUtil.loadFileBytes(file));
        if (encoding != null) {
          return encoding;
        }
      }
      catch (IOException e) {
        LOG.info("Cannot detect encoding for xml file " + file.getAbsolutePath(), e);
      }
    }

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

  private static boolean isXmlFile(File file) {
    String fileName = file.getName();
    return SystemInfoRt.isFileSystemCaseSensitive
           ? fileName.endsWith(XML_NAME_SUFFIX)
           : Strings.endsWithIgnoreCase(fileName, XML_NAME_SUFFIX);
  }

  @Override
  public @NotNull Map<String, String> getUrlToEncoding() {
    return Collections.unmodifiableMap(myUrlToEncoding);
  }

  @Override
  public @Nullable String getProjectEncoding() {
    return myProjectEncoding;
  }

  @Override
  public @NotNull JpsEncodingProjectConfigurationImpl createCopy() {
    return new JpsEncodingProjectConfigurationImpl(myUrlToEncoding, myProjectEncoding);
  }
}
