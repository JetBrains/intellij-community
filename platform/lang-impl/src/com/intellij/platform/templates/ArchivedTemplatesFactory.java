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

import com.intellij.icons.AllIcons;
import com.intellij.ide.fileTemplates.impl.UrlUtil;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 * @since 10/1/12
 */
public class ArchivedTemplatesFactory extends ProjectTemplatesFactory {
  private final static Logger LOG = Logger.getInstance(ArchivedTemplatesFactory.class);

  static final String ZIP = ".zip";

  private final ClearableLazyValue<MultiMap<String, Pair<URL, ClassLoader>>> myGroups = new ClearableLazyValue<MultiMap<String, Pair<URL, ClassLoader>>>() {
    @NotNull
    @Override
    protected MultiMap<String, Pair<URL, ClassLoader>> compute() {
      MultiMap<String, Pair<URL, ClassLoader>> map = MultiMap.createSmart();
      Map<URL, ClassLoader> urls = new THashMap<URL, ClassLoader>();
      //for (IdeaPluginDescriptor plugin : plugins) {
      //  if (!plugin.isEnabled()) continue;
      //  try {
      //    ClassLoader loader = plugin.getPluginClassLoader();
      //    Enumeration<URL> resources = loader.getResources("resources/projectTemplates");
      //    ArrayList<URL> list = Collections.list(resources);
      //    for (URL url : list) {
      //      urls.put(url, loader);
      //    }
      //  }
      //  catch (IOException e) {
      //    LOG.error(e);
      //  }
      //}

      URL configURL = getCustomTemplatesURL();
      urls.put(configURL, ClassLoader.getSystemClassLoader());

      for (Map.Entry<URL, ClassLoader> url : urls.entrySet()) {
        try {
          List<String> children = UrlUtil.getChildrenRelativePaths(url.getKey());
          if (configURL == url.getKey() && !children.isEmpty()) {
            map.putValue(CUSTOM_GROUP, Pair.create(url.getKey(), url.getValue()));
            continue;
          }

          for (String child : children) {
            int index = child.indexOf('/');
            if (index != -1) {
              child = child.substring(0, index);
            }
            String name = child.replace('_', ' ');
            map.putValue(name, Pair.create(new URL(url.getKey().toExternalForm() + "/" + child), url.getValue()));
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
      return map;
    }
  };

  @NotNull
  private static URL getCustomTemplatesURL() {
    try {
      return new File(getCustomTemplatesPath()).toURI().toURL();
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  static String getCustomTemplatesPath() {
    return PathManager.getConfigPath() + "/projectTemplates";
  }

  public static File getTemplateFile(String name) {
    return new File(getCustomTemplatesPath() + "/" + name + ".zip");
  }

  @NotNull
  @Override
  public String[] getGroups() {
    myGroups.drop();
    Set<String> groups = myGroups.getValue().keySet();
    return ArrayUtil.toStringArray(groups);
  }

  @NotNull
  @Override
  public ProjectTemplate[] createTemplates(@Nullable String group, WizardContext context) {
    // myGroups contains only not-null keys
    if (group == null) {
      return ProjectTemplate.EMPTY_ARRAY;
    }

    List<ProjectTemplate> templates = null;
    for (Pair<URL, ClassLoader> url : myGroups.getValue().get(group)) {
      try {
        for (String child : UrlUtil.getChildrenRelativePaths(url.first)) {
          if (child.endsWith(ZIP)) {
            if (templates == null) {
              templates = new SmartList<ProjectTemplate>();
            }
            templates.add(new LocalArchivedTemplate(new URL(url.first.toExternalForm() + '/' + child), url.second));
          }
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return ContainerUtil.isEmpty(templates) ? ProjectTemplate.EMPTY_ARRAY : templates.toArray(new ProjectTemplate[templates.size()]);
  }

  @Override
  public int getGroupWeight(String group) {
    return CUSTOM_GROUP.equals(group) ? -2 : 0;
  }

  @Override
  public Icon getGroupIcon(String group) {
    return CUSTOM_GROUP.equals(group) ? AllIcons.Modules.Types.UserDefined : super.getGroupIcon(group);
  }
}
