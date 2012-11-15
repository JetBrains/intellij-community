/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.platform.templates;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * @author Dmitry Avdeev
 *         Date: 11/14/12
 */
public class RemoteTemplatesFactory implements ProjectTemplatesFactory {

  private static final String URL = "http://download.jetbrains.com/idea/project_templates/";

  @NotNull
  @Override
  public String[] getGroups() {
    return new String[] { "Samples Gallery"};
  }

  @NotNull
  @Override
  public ProjectTemplate[] createTemplates(String group, WizardContext context) {
    InputStream stream = null;
    HttpURLConnection connection = null;
    String code = ApplicationInfo.getInstance().getBuild().getProductCode();
    try {
      connection = getConnection(code + "_templates.xml");
      stream = connection.getInputStream();
      String text = StreamUtil.readText(stream);
      return createFromText(text);
    }
    catch (Exception e) {
      LOG.error(e);
      return ProjectTemplate.EMPTY_ARRAY;
    }
    finally {
      StreamUtil.closeStream(stream);
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  @SuppressWarnings("unchecked")
  public static ProjectTemplate[] createFromText(String text) throws IOException, JDOMException {

    List<Element> elements = JDOMUtil.loadDocument(text).getRootElement().getChildren("template");

    List<ProjectTemplate> templates = ContainerUtil.mapNotNull(elements, new NullableFunction<Element, ProjectTemplate>() {
      @Override
      public ProjectTemplate fun(final Element element) {

        List<Element> plugins = element.getChildren("requiredPlugin");
        for (Element plugin : plugins) {
          String id = plugin.getTextTrim();
          if (!PluginManager.isPluginInstalled(PluginId.getId(id))) {
            return null;
          }
        }
        String type = element.getChildText("moduleType");
        final ModuleType moduleType = ModuleTypeManager.getInstance().findByID(type);
        return new ArchivedProjectTemplate(element.getChildTextTrim("name")) {
          @Override
          protected ModuleType getModuleType() {
            return moduleType;
          }

          @Override
          public ZipInputStream getStream() throws IOException {
            String path = element.getChildText("path");
            final HttpURLConnection connection = getConnection(path);
            return new ZipInputStream(connection.getInputStream()) {
              @Override
              public void close() throws IOException {
                super.close();
                connection.disconnect();
              }
            };
          }

          @Nullable
          @Override
          public String getDescription() {
            return element.getChildTextTrim("description");
          }
        };
      }
    });
    return templates.toArray(new ProjectTemplate[templates.size()]);
  }

  private static HttpURLConnection getConnection(String path) throws IOException {
    HttpURLConnection connection = HttpConfigurable.getInstance().openHttpConnection(URL + path);
    connection.setConnectTimeout(2000);
    connection.setReadTimeout(2000);
    connection.connect();
    return connection;
  }

  private final static Logger LOG = Logger.getInstance(RemoteTemplatesFactory.class);
}
