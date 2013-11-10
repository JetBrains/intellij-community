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

import com.intellij.ide.fileTemplates.impl.UrlUtil;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * @author Dmitry Avdeev
 * @since 10/1/12
 */
public class ArchivedTemplatesFactory extends ProjectTemplatesFactory {

  static final String ZIP = ".zip";

  private final ClearableLazyValue<MultiMap<String, Pair<URL, ClassLoader>>> myGroups = new ClearableLazyValue<MultiMap<String, Pair<URL, ClassLoader>>>() {
    @NotNull
    @Override
    protected MultiMap<String, Pair<URL, ClassLoader>> compute() {
      MultiMap<String, Pair<URL, ClassLoader>> map = new MultiMap<String, Pair<URL, ClassLoader>>();
      IdeaPluginDescriptor[] plugins = PluginManagerCore.getPlugins();
      Map<URL, ClassLoader> urls = new HashMap<URL, ClassLoader>();
      for (IdeaPluginDescriptor plugin : plugins) {
        if (!plugin.isEnabled()) continue;
        try {
          ClassLoader loader = plugin.getPluginClassLoader();
          Enumeration<URL> resources = loader.getResources("resources/projectTemplates");
          ArrayList<URL> list = Collections.list(resources);
          for (URL url : list) {
            urls.put(url, loader);
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }

      URL configURL = getCustomTemplatesURL();
      if (configURL != null) {
        urls.put(configURL, ClassLoader.getSystemClassLoader());
      }

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

  private static URL getCustomTemplatesURL() {
    String path = getCustomTemplatesPath();
    try {
      return new File(path).toURI().toURL();
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  static String getCustomTemplatesPath() {
    return PathManager.getConfigPath() + "/projectTemplates";
  }

  public static File getTemplateFile(String name) {
    String configURL = getCustomTemplatesPath();
    return new File(configURL + "/" + name + ".zip");
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
  public ProjectTemplate[] createTemplates(String group, WizardContext context) {
    Collection<Pair<URL, ClassLoader>> urls = myGroups.getValue().get(group);
    List<ProjectTemplate> templates = new ArrayList<ProjectTemplate>();
    for (Pair<URL, ClassLoader> url : urls) {
      try {
        List<String> children = UrlUtil.getChildrenRelativePaths(url.first);
        for (String child : children) {
          if (child.endsWith(ZIP)) {
            URL templateUrl = new URL(url.first.toExternalForm() + "/" + child);
            templates.add(new LocalArchivedTemplate(templateUrl, url.second));
          }
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return templates.toArray(new ProjectTemplate[templates.size()]);
  }

  @Override
  public int getGroupWeight(String group) {
    return CUSTOM_GROUP.equals(group) ? -2 : 0;
  }

  private final static Logger LOG = Logger.getInstance(ArchivedTemplatesFactory.class);
}
