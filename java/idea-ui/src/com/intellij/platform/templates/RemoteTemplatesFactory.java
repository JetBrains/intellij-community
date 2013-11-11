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
import com.intellij.ide.util.projectWizard.ProjectTemplateParameterFactory;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.util.projectWizard.WizardInputField;
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
  public static final String SAMPLES_GALLERY = "Samples Gallery";
  private static final Namespace NAMESPACE = Namespace.getNamespace("http://www.jetbrains.com/projectTemplates");

  public static final String INPUT_FIELD = "input-field";
  public static final String TEMPLATE = "template";
  public static final String INPUT_DEFAULT = "default";

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

    Element rootElement = JDOMUtil.loadDocument(text).getRootElement();
    List<Element> groups = rootElement.getChildren("group", NAMESPACE);
    MultiMap<String, ArchivedProjectTemplate> map = new MultiMap<String, ArchivedProjectTemplate>();
    if (groups.isEmpty()) { // sample gallery by default
      map.put(SAMPLES_GALLERY, createGroupTemplates(rootElement, Namespace.NO_NAMESPACE));
    }
    else {
      for (Element group : groups) {
        if (checkRequiredPlugins(group, NAMESPACE)) {
          map.put(group.getChildText("name", NAMESPACE), createGroupTemplates(group, NAMESPACE));
        }
      }
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

        final List<WizardInputField> inputFields = getFields(element, ns);
        final String path = element.getChildText("path", ns);
        final String description = element.getChildTextTrim("description", ns);
        String name = element.getChildTextTrim("name", ns);
        return new ArchivedProjectTemplate(name, element.getChildTextTrim("category")) {
          @Override
          protected ModuleType getModuleType() {
            return moduleType;
          }

          @Override
          public List<WizardInputField> getInputFields() {
            return inputFields;
          }

          @Override
          public ZipInputStream getStream() throws IOException {
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
            return description;
          }
        };
      }
    });
  }

  static List<WizardInputField> getFields(Element templateElement, final Namespace ns) {
    //noinspection unchecked
    return ContainerUtil.mapNotNull(templateElement.getChildren(INPUT_FIELD, ns), new Function<Element, WizardInputField>() {
      @Override
      public WizardInputField fun(Element element) {
        ProjectTemplateParameterFactory factory = WizardInputField.getFactoryById(element.getText());
        return factory == null ? null : factory.createField(element.getAttributeValue(INPUT_DEFAULT));
      }
    });
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
}
