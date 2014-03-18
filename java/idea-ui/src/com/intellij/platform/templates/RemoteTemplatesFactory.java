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
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.net.HttpConfigurable;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * @author Dmitry Avdeev
 *         Date: 11/14/12
 */
public class RemoteTemplatesFactory extends ProjectTemplatesFactory {

  private static final String URL = "http://download.jetbrains.com/idea/project_templates/";

  public static final String TEMPLATE = "template";
  public static final String INPUT_DEFAULT = "default";
  public static final Function<Element, String> ELEMENT_STRING_FUNCTION = new Function<Element, String>() {
    @Override
    public String fun(Element element) {
      return element.getText();
    }
  };

  private final ClearableLazyValue<MultiMap<String, ArchivedProjectTemplate>> myTemplates = new ClearableLazyValue<MultiMap<String, ArchivedProjectTemplate>>() {
    @NotNull
    @Override
    protected MultiMap<String, ArchivedProjectTemplate> compute() {
      return getTemplates();
    }
  };

  @NotNull
  @Override
  public String[] getGroups() {
    myTemplates.drop();
    return ArrayUtil.toStringArray(myTemplates.getValue().keySet());
  }

  @NotNull
  @Override
  public ProjectTemplate[] createTemplates(String group, WizardContext context) {
    Collection<ArchivedProjectTemplate> templates = myTemplates.getValue().get(group);
    return templates.toArray(new ProjectTemplate[templates.size()]);
  }

  private static MultiMap<String, ArchivedProjectTemplate> getTemplates() {
    InputStream stream = null;
    HttpURLConnection connection = null;
    String code = ApplicationInfo.getInstance().getBuild().getProductCode();
    try {
      connection = getConnection(code + "_templates.xml");
      stream = connection.getInputStream();
      String text = StreamUtil.readText(stream, TemplateModuleBuilder.UTF_8);
      return createFromText(text);
    }
    catch (IOException ex) {  // timeouts, lost connection etc
      LOG.info(ex);
      return MultiMap.emptyInstance();
    }
    catch (Exception e) {
      LOG.error(e);
      return MultiMap.emptyInstance();
    }
    finally {
      StreamUtil.closeStream(stream);
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  @SuppressWarnings("unchecked")
  public static MultiMap<String, ArchivedProjectTemplate> createFromText(String text) throws IOException, JDOMException {

    MultiMap<String, ArchivedProjectTemplate> map = new MultiMap<String, ArchivedProjectTemplate>();
    Element rootElement = JDOMUtil.loadDocument(text).getRootElement();
    List<ArchivedProjectTemplate> templates = createGroupTemplates(rootElement, Namespace.NO_NAMESPACE);
    for (ArchivedProjectTemplate template : templates) {
      map.putValue(template.getCategory(), template);
    }
    return map;
  }

  @SuppressWarnings("unchecked")
  private static List<ArchivedProjectTemplate> createGroupTemplates(Element groupElement, final Namespace ns) {
    List<Element> elements = groupElement.getChildren(TEMPLATE, ns);

    return ContainerUtil.mapNotNull(elements, new NullableFunction<Element, ArchivedProjectTemplate>() {
      @Override
      public ArchivedProjectTemplate fun(final Element element) {

        if (!checkRequiredPlugins(element, ns)) return null;
        String type = element.getChildText("moduleType");

        final ModuleType moduleType = ModuleTypeManager.getInstance().findByID(type);

        final String path = element.getChildText("path", ns);
        final String description = element.getChildTextTrim("description", ns);
        String name = element.getChildTextTrim("name", ns);
        RemoteProjectTemplate template = new RemoteProjectTemplate(name, element, moduleType, path, description);
        template.populateFromElement(element, ns);
        return template;
      }
    });
  }

  public static List<String> getFrameworks(Element element) {
    List<Element> frameworks = element.getChildren("framework");
    return ContainerUtil.map(frameworks, ELEMENT_STRING_FUNCTION);
  }

  private static boolean checkRequiredPlugins(Element element, Namespace ns) {
    @SuppressWarnings("unchecked") List<Element> plugins = element.getChildren("requiredPlugin", ns);
    for (Element plugin : plugins) {
      String id = plugin.getTextTrim();
      if (!PluginManager.isPluginInstalled(PluginId.getId(id))) {
        return false;
      }
    }
    return true;
  }

  private static HttpURLConnection getConnection(String path) throws IOException {
    HttpURLConnection connection = HttpConfigurable.getInstance().openHttpConnection(URL + path);
    connection.setConnectTimeout(2000);
    connection.setReadTimeout(2000);
    connection.connect();
    return connection;
  }

  private final static Logger LOG = Logger.getInstance(RemoteTemplatesFactory.class);

  private static class RemoteProjectTemplate extends ArchivedProjectTemplate {
    private final ModuleType myModuleType;
    private final String myPath;
    private final String myDescription;

    public RemoteProjectTemplate(String name,
                                 Element element,
                                 ModuleType moduleType,
                                 String path, String description) {
      super(name, element.getChildTextTrim("category"));
      myModuleType = moduleType;
      myPath = path;
      myDescription = description;
    }

    @Override
    protected ModuleType getModuleType() {
      return myModuleType;
    }

    @Override
    public ZipInputStream getStream() throws IOException {
      final HttpURLConnection connection = getConnection(myPath);
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
      return myDescription;
    }
  }
}
