// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEncodingConfigurationService;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JpsEncodingModelSerializerExtension extends JpsModelSerializerExtension {
  @NotNull
  @Override
  public List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Collections.singletonList(new JpsEncodingConfigurationSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsGlobalExtensionSerializer> getGlobalExtensionSerializers() {
    return Collections.singletonList(new JpsGlobalEncodingSerializer());
  }

  private static final class JpsEncodingConfigurationSerializer extends JpsProjectExtensionSerializer {
    private JpsEncodingConfigurationSerializer() {
      super("encodings.xml", "Encoding");
    }

    @Override
    public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
      String projectEncoding = null;
      Map<String, String> urlToEncoding = new HashMap<>();
      for (Element fileTag : JDOMUtil.getChildren(componentTag, "file")) {
        String url = fileTag.getAttributeValue("url");
        String encoding = fileTag.getAttributeValue("charset");
        if (url.equals("PROJECT")) {
          projectEncoding = encoding;
        }
        else {
          urlToEncoding.put(url, encoding);
        }
      }
      JpsEncodingConfigurationService.getInstance().setEncodingConfiguration(project, projectEncoding, urlToEncoding);
    }

    @Override
    public void saveExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
    }
  }

  private static final class JpsGlobalEncodingSerializer extends JpsGlobalExtensionSerializer {
    public static final String ENCODING_ATTRIBUTE = "default_encoding";

    private JpsGlobalEncodingSerializer() {
      super("encoding.xml", "Encoding");
    }

    @Override
    public void loadExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
      String encoding = componentTag.getAttributeValue(ENCODING_ATTRIBUTE);
      JpsEncodingConfigurationService.getInstance().setGlobalEncoding(global, StringUtil.nullize(encoding));
    }

    @Override
    public void loadExtensionWithDefaultSettings(@NotNull JpsGlobal global) {
      JpsEncodingConfigurationService.getInstance().setGlobalEncoding(global, CharsetToolkit.UTF8);
    }

    @Override
    public void saveExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
      componentTag.setAttribute(ENCODING_ATTRIBUTE, JpsEncodingConfigurationService.getInstance().getGlobalEncoding(global));
    }
  }
}
