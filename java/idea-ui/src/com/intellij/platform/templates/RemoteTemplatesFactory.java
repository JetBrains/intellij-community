// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.templates;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.JdomKt;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.HttpRequests;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * @author Dmitry Avdeev
 */
public class RemoteTemplatesFactory extends ProjectTemplatesFactory {
  private final static Logger LOG = Logger.getInstance(RemoteTemplatesFactory.class);

  private static final String URL = "https://download.jetbrains.com/idea/project_templates/";

  private final ClearableLazyValue<MultiMap<String, ArchivedProjectTemplate>> myTemplates = ClearableLazyValue.create(() -> {
    try {
      return HttpRequests.request(URL + ApplicationInfo.getInstance().getBuild().getProductCode() + "_templates.xml")
        .connect(request -> {
          try {
            return create(JdomKt.loadElement(request.getReader()));
          }
          catch (JDOMException e) {
            LOG.error(e);
            return MultiMap.emptyInstance();
          }
        });
    }
    catch (IOException e) {  // timeouts, lost connection etc
      LOG.info(e);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return MultiMap.emptyInstance();
  });

  @NotNull
  @Override
  public String[] getGroups() {
    myTemplates.drop();
    return ArrayUtil.toStringArray(myTemplates.getValue().keySet());
  }

  @NotNull
  @Override
  public ProjectTemplate[] createTemplates(@Nullable String group, WizardContext context) {
    Collection<ArchivedProjectTemplate> templates = myTemplates.getValue().get(group);
    return templates.isEmpty() ? ProjectTemplate.EMPTY_ARRAY : templates.toArray(ProjectTemplate.EMPTY_ARRAY);
  }

  @NotNull
  @TestOnly
  public static MultiMap<String, ArchivedProjectTemplate> createFromText(@NotNull String value) throws IOException, JDOMException {
    return create(JdomKt.loadElement(value));
  }

  @NotNull
  private static MultiMap<String, ArchivedProjectTemplate> create(@NotNull Element element) {
    MultiMap<String, ArchivedProjectTemplate> map = MultiMap.create();
    for (ArchivedProjectTemplate template : createGroupTemplates(element)) {
      map.putValue(template.getCategory(), template);
    }
    return map;
  }

  private static List<ArchivedProjectTemplate> createGroupTemplates(Element groupElement) {
    List<Element> children = groupElement.getChildren(ArchivedProjectTemplate.TEMPLATE);
    return ContainerUtil.mapNotNull(children, (NullableFunction<Element, ArchivedProjectTemplate>)element -> {
      if (!checkRequiredPlugins(element)) {
        return null;
      }

      final ModuleType moduleType = ModuleTypeManager.getInstance().findByID(element.getChildText("moduleType"));
      final String path = element.getChildText("path");
      final String description = element.getChildTextTrim("description");
      String name = element.getChildTextTrim("name");
      RemoteProjectTemplate template = new RemoteProjectTemplate(name, element, moduleType, path, description);
      template.populateFromElement(element);
      return template;
    });
  }

  private static boolean checkRequiredPlugins(Element element) {
    for (Element plugin : element.getChildren("requiredPlugin")) {
      if (!PluginManager.isPluginInstalled(PluginId.getId(plugin.getTextTrim()))) {
        return false;
      }
    }
    return true;
  }

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
    public <T> T processStream(@NotNull final StreamProcessor<T> consumer) throws IOException {
      return HttpRequests.request(URL + myPath).connect(request -> consumeZipStream(consumer, new ZipInputStream(request.getInputStream())));
    }

    @Nullable
    @Override
    public String getDescription() {
      return myDescription;
    }
  }
}
