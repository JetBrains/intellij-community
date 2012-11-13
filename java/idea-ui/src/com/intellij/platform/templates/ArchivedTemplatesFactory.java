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
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * @author Dmitry Avdeev
 *         Date: 10/1/12
 */
public class ArchivedTemplatesFactory implements ProjectTemplatesFactory {

  private static final String ZIP = ".zip";

  private final NotNullLazyValue<MultiMap<String, URL>> myGroups = new NotNullLazyValue<MultiMap<String, URL>>() {
    @NotNull
    @Override
    protected MultiMap<String, URL> compute() {
      MultiMap<String, URL> map = new MultiMap<String, URL>();
      IdeaPluginDescriptor[] plugins = PluginManager.getPlugins();
      Set<URL> urls = new HashSet<URL>();
      for (IdeaPluginDescriptor plugin : plugins) {
        try {
          Enumeration<URL> resources = plugin.getPluginClassLoader().getResources("resources/projectTemplates");
          urls.addAll(Collections.list(resources));
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }

      URL configURL = getCustomTemplatesURL();
      ContainerUtil.addIfNotNull(urls, configURL);

      for (URL url : urls) {
        try {
          List<String> children = UrlUtil.getChildrenRelativePaths(url);
          if (configURL == url && !children.isEmpty()) {
            map.putValue(CUSTOM_GROUP, url);
            continue;
          }

          for (String child : children) {
            int index = child.indexOf('/');
            if (index != -1) {
              child = child.substring(0, index);
            }
            map.putValue(child.replace('_', ' '), new URL(url.toExternalForm() + "/" + child));
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
    return PathManager.getConfigPath() + "/resources/projectTemplates";
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

    Collection<URL> urls = myGroups.getValue().get(group);
    List<ProjectTemplate> templates = new ArrayList<ProjectTemplate>();
    for (URL url : urls) {
      try {
        List<String> children = UrlUtil.getChildrenRelativePaths(url);
        for (String child : children) {
          if (child.endsWith(ZIP)) {
            URL templateUrl = new URL(url.toExternalForm() + "/" + child);
            String name = child.substring(0, child.length() - ZIP.length()).replace('_', ' ');
            templates.add(new ArchivedProjectTemplate(name, templateUrl));
          }
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return templates.toArray(new ProjectTemplate[templates.size()]);
  }

  private final static Logger LOG = Logger.getInstance(ArchivedTemplatesFactory.class);
}
