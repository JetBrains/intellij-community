/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;

/**
 * Serves as a container for all existing template manager types and loads corresponding templates upon creation (at construction time).
 *
 * @author Rustam Vishnyakov
 */
public class FileTemplatesLoader {
  private static final Logger LOG = Logger.getInstance("#" + FileTemplatesLoader.class.getName());

  private static final String TEMPLATES_DIR = "fileTemplates";
  private static final String DEFAULT_TEMPLATES_ROOT = TEMPLATES_DIR;
  public static final String DESCRIPTION_FILE_EXTENSION = "html";
  private static final String DESCRIPTION_EXTENSION_SUFFIX = "." + DESCRIPTION_FILE_EXTENSION;
  //static final String DESCRIPTION_FILE_NAME = "default." + DESCRIPTION_FILE_EXTENSION;

  private final FTManager myDefaultTemplatesManager;
  private final FTManager myInternalTemplatesManager;
  private final FTManager myPatternsManager;
  private final FTManager myCodeTemplatesManager;
  private final FTManager myJ2eeTemplatesManager;

  private final Map<String, FTManager> myDirToManagerMap = new HashMap<String, FTManager>();
  private final FTManager[] myAllManagers;

  private static final String INTERNAL_DIR = "internal";
  private static final String INCLUDES_DIR = "includes";
  private static final String CODETEMPLATES_DIR = "code";
  private static final String J2EE_TEMPLATES_DIR = "j2ee";
  private static final String ROOT_DIR = ".";
  private final FileTypeManagerEx myTypeManager;

  private URL myDefaultTemplateDescription;
  private URL myDefaultIncludeDescription;

  public FileTemplatesLoader(@NotNull FileTypeManagerEx typeManager) {
    myTypeManager = typeManager;
    myDefaultTemplatesManager = new FTManager(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY, ROOT_DIR);
    myInternalTemplatesManager = new FTManager(FileTemplateManager.INTERNAL_TEMPLATES_CATEGORY, INTERNAL_DIR, true);
    myPatternsManager = new FTManager(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY, INCLUDES_DIR);
    myCodeTemplatesManager = new FTManager(FileTemplateManager.CODE_TEMPLATES_CATEGORY, CODETEMPLATES_DIR);
    myJ2eeTemplatesManager = new FTManager(FileTemplateManager.J2EE_TEMPLATES_CATEGORY, J2EE_TEMPLATES_DIR);
    myAllManagers = new FTManager[]{
      myDefaultTemplatesManager,
      myInternalTemplatesManager,
      myPatternsManager,
      myCodeTemplatesManager,
      myJ2eeTemplatesManager};

    myDirToManagerMap.put("", myDefaultTemplatesManager);
    myDirToManagerMap.put(INTERNAL_DIR + "/", myInternalTemplatesManager);
    myDirToManagerMap.put(INCLUDES_DIR + "/", myPatternsManager);
    myDirToManagerMap.put(CODETEMPLATES_DIR + "/", myCodeTemplatesManager);
    myDirToManagerMap.put(J2EE_TEMPLATES_DIR + "/", myJ2eeTemplatesManager);

    loadDefaultTemplates();
    for (FTManager child : myAllManagers) {
      loadCustomizedContent(child);
    }
  }

  @NotNull
  public FTManager[] getAllManagers() {
    return myAllManagers;
  }

  @NotNull
  public FTManager getDefaultTemplatesManager() {
    return myDefaultTemplatesManager;
  }

  @NotNull
  public FTManager getInternalTemplatesManager() {
    return myInternalTemplatesManager;
  }

  public FTManager getPatternsManager() {
    return myPatternsManager;
  }

  public FTManager getCodeTemplatesManager() {
    return myCodeTemplatesManager;
  }

  public FTManager getJ2eeTemplatesManager() {
    return myJ2eeTemplatesManager;
  }

  public URL getDefaultTemplateDescription() {
    return myDefaultTemplateDescription;
  }

  public URL getDefaultIncludeDescription() {
    return myDefaultIncludeDescription;
  }

  private void loadDefaultTemplates() {
    final Set<URL> processedUrls = new HashSet<URL>();
    for (PluginDescriptor plugin : PluginManagerCore.getPlugins()) {
      if (plugin instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)plugin).isEnabled()) {
        final ClassLoader loader = plugin.getPluginClassLoader();
        if (loader instanceof PluginClassLoader && ((PluginClassLoader)loader).getUrls().isEmpty()) {
          continue; // development mode, when IDEA_CORE's loader contains all the classpath
        }
        try {
          final Enumeration<URL> systemResources = loader.getResources(DEFAULT_TEMPLATES_ROOT);
          if (systemResources != null && systemResources.hasMoreElements()) {
            while (systemResources.hasMoreElements()) {
              final URL url = systemResources.nextElement();
              if (processedUrls.contains(url)) {
                continue;
              }
              processedUrls.add(url);
              loadDefaultsFromRoot(url);
            }
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  private void loadDefaultsFromRoot(final URL root) throws IOException {
    final List<String> children = UrlUtil.getChildrenRelativePaths(root);
    if (children.isEmpty()) {
      return;
    }
    final Set<String> descriptionPaths = new HashSet<String>();
    for (String path : children) {
      if (path.equals("default.html")) {
        myDefaultTemplateDescription = UrlClassLoader.internProtocol(new URL(root.toExternalForm() + "/" + path));
      }
      else if (path.equals("includes/default.html")) {
        myDefaultIncludeDescription = UrlClassLoader.internProtocol(new URL(root.toExternalForm() + "/" + path));
      }
      else if (path.endsWith(DESCRIPTION_EXTENSION_SUFFIX)) {
        descriptionPaths.add(path);
      }
    }
    for (final String path : children) {
      for (Map.Entry<String, FTManager> entry : myDirToManagerMap.entrySet()) {
        final String prefix = entry.getKey();
        if (matchesPrefix(path, prefix)) {
          if (path.endsWith(FTManager.TEMPLATE_EXTENSION_SUFFIX)) {
            final String filename = path.substring(prefix.length(), path.length() - FTManager.TEMPLATE_EXTENSION_SUFFIX.length());
            final String extension = myTypeManager.getExtension(filename);
            final String templateName = filename.substring(0, filename.length() - extension.length() - 1);
            final URL templateUrl = UrlClassLoader.internProtocol(new URL(root.toExternalForm() + "/" + path));
            final String descriptionPath = getDescriptionPath(prefix, templateName, extension, descriptionPaths);
            final URL descriptionUrl = descriptionPath != null ? UrlClassLoader.internProtocol(new URL(root.toExternalForm() + "/" + descriptionPath)) : null;
            entry.getValue().addDefaultTemplate(new DefaultTemplate(templateName, extension, templateUrl, descriptionUrl));
          }
          break; // FTManagers loop
        }
      }
    }
  }

  void loadCustomizedContent(FTManager manager) {
    final File configRoot = manager.getConfigRoot(false);
    final File[] configFiles = configRoot.listFiles();
    if (configFiles == null) {
      return;
    }

    final List<File> templateWithDefaultExtension = new ArrayList<File>();
    final Set<String> processedNames = new HashSet<String>();

    for (File file : configFiles) {
      if (file.isDirectory() || myTypeManager.isFileIgnored(file.getName()) || file.isHidden()) {
        continue;
      }
      final String name = file.getName();
      if (name.endsWith(FTManager.TEMPLATE_EXTENSION_SUFFIX)) {
        templateWithDefaultExtension.add(file);
      }
      else {
        processedNames.add(name);
        addTemplateFromFile(manager, name, file);
      }
    }

    for (File file : templateWithDefaultExtension) {
      String name = file.getName();
      // cut default template extension
      name = name.substring(0, name.length() - FTManager.TEMPLATE_EXTENSION_SUFFIX.length());
      if (!processedNames.contains(name)) {
        addTemplateFromFile(manager, name, file);
      }
      FileUtil.delete(file);
    }
  }

  private static void addTemplateFromFile(FTManager manager, String fileName, File file) {
    Pair<String,String> nameExt = FTManager.decodeFileName(fileName);
    final String extension = nameExt.second;
    final String templateQName = nameExt.first;
    if (templateQName.length() == 0) {
      return;
    }
    try {
      final String text = FileUtil.loadFile(file, FTManager.CONTENT_ENCODING);
      manager.addTemplate(templateQName, extension).setText(text);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static boolean matchesPrefix(String path, String prefix) {
    if (prefix.length() == 0) {
      return !path.contains("/");
    }
    return FileUtil.startsWith(path, prefix) && !path.substring(prefix.length()).contains("/");
  }

  //Example: templateName="NewClass"   templateExtension="java"
  @Nullable
  private static String getDescriptionPath(String pathPrefix, String templateName, String templateExtension, Set<String> descriptionPaths) {
    final Locale locale = Locale.getDefault();

    String descName = MessageFormat
      .format("{0}.{1}_{2}_{3}" + DESCRIPTION_EXTENSION_SUFFIX, templateName, templateExtension,
              locale.getLanguage(), locale.getCountry());
    String descPath = pathPrefix.length() > 0 ? pathPrefix + descName : descName;
    if (descriptionPaths.contains(descPath)) {
      return descPath;
    }

    descName = MessageFormat.format("{0}.{1}_{2}" + DESCRIPTION_EXTENSION_SUFFIX, templateName, templateExtension, locale.getLanguage());
    descPath = pathPrefix.length() > 0 ? pathPrefix + descName : descName;
    if (descriptionPaths.contains(descPath)) {
      return descPath;
    }

    descName = templateName + "." + templateExtension + DESCRIPTION_EXTENSION_SUFFIX;
    descPath = pathPrefix.length() > 0 ? pathPrefix + descName : descName;
    if (descriptionPaths.contains(descPath)) {
      return descPath;
    }
    return null;
  }
}
