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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * @author Dmitry Avdeev
 *         Date: 10/1/12
 */
public class ArchivedTemplatesFactory implements ProjectTemplatesFactory {

  private static final String ZIP = ".zip";

  @NotNull
  @Override
  public ProjectTemplate[] createTemplates(WizardContext context) {

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

    List<ProjectTemplate> templates = new ArrayList<ProjectTemplate>();
    for (URL url : urls) {
      try {
        final List<String> children = UrlUtil.getChildrenRelativePaths(url);
        for (String child : children) {
          if (child.endsWith(ZIP)) {
            final URL templateUrl = new URL(url.toExternalForm() + "/" + child);
            templates.add(new ArchivedProjectTemplate(child.substring(0, child.length() - ZIP.length()), templateUrl, context));
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
