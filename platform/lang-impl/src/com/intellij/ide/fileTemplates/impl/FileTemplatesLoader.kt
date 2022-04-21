// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.project.ProjectKt;
import com.intellij.util.Function;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.URLUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.apache.velocity.runtime.ParserPool;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.RuntimeSingleton;
import org.apache.velocity.runtime.directive.Stop;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Supplier;

/**
 * Serves as a container for all existing template manager types and loads corresponding templates lazily.
 * Reloads templates on plugins change.
 */
class FileTemplatesLoader implements Disposable {
  private static final Logger LOG = Logger.getInstance(FileTemplatesLoader.class);

  static final String TEMPLATES_DIR = "fileTemplates";
  private static final String DEFAULT_TEMPLATES_ROOT = TEMPLATES_DIR;
  private static final String DESCRIPTION_FILE_EXTENSION = "html";
  private static final String DESCRIPTION_EXTENSION_SUFFIX = "." + DESCRIPTION_FILE_EXTENSION;

  private static final Map<String, String> MANAGER_TO_DIR = Map.of(
    FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY, "",
    FileTemplateManager.INTERNAL_TEMPLATES_CATEGORY, "internal",
    FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY, "includes",
    FileTemplateManager.CODE_TEMPLATES_CATEGORY, "code",
    FileTemplateManager.J2EE_TEMPLATES_CATEGORY, "j2ee"
  );

  private final ClearableLazyValue<LoadedConfiguration> myManagers;

  FileTemplatesLoader(@Nullable Project project) {
    myManagers = ClearableLazyValue.createAtomic(() -> loadConfiguration(project));
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        // this shouldn't be necessary once we update to a new Velocity Engine with this leak fixed (IDEA-240449, IDEABKL-7932)
        clearClassLeakViaStaticExceptionTrace();
        resetParserPool();
      }

      private void clearClassLeakViaStaticExceptionTrace() {
        Field field = ReflectionUtil.getDeclaredField(Stop.class, "STOP_ALL");
        if (field != null) {
          try {
            ThrowableInterner.clearBacktrace((Throwable)field.get(null));
          }
          catch (Throwable e) {
            LOG.info(e);
          }
        }
      }

      private void resetParserPool() {
        try {
          RuntimeServices ri = RuntimeSingleton.getRuntimeServices();
          Field ppField = ReflectionUtil.getDeclaredField(ri.getClass(), "parserPool");
          if (ppField != null) {
            Object pp = ppField.get(ri);
            if (pp instanceof ParserPool) {
              ((ParserPool)pp).initialize(ri);
            }
          }
        }
        catch (Throwable e) {
          LOG.info(e);
        }
      }

      @Override
      public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        myManagers.drop();
      }

      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        myManagers.drop();
      }
    });
  }

  @Override
  public void dispose() {}

  @NotNull Collection<@NotNull FTManager> getAllManagers() {
    return myManagers.getValue().getManagers();
  }

  @NotNull
  FTManager getDefaultTemplatesManager() {
    return new FTManager(myManagers.getValue().getManager(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY));
  }

  @NotNull
  FTManager getInternalTemplatesManager() {
    return new FTManager(myManagers.getValue().getManager(FileTemplateManager.INTERNAL_TEMPLATES_CATEGORY));
  }

  @NotNull
  FTManager getPatternsManager() {
    return new FTManager(myManagers.getValue().getManager(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY));
  }

  @NotNull
  FTManager getCodeTemplatesManager() {
    return new FTManager(myManagers.getValue().getManager(FileTemplateManager.CODE_TEMPLATES_CATEGORY));
  }

  @NotNull
  FTManager getJ2eeTemplatesManager() {
    return new FTManager(myManagers.getValue().getManager(FileTemplateManager.J2EE_TEMPLATES_CATEGORY));
  }

  Supplier<String> getDefaultTemplateDescription() {
    return myManagers.getValue().defaultTemplateDescription;
  }

  Supplier<String> getDefaultIncludeDescription() {
    return myManagers.getValue().defaultIncludeDescription;
  }

  private static LoadedConfiguration loadConfiguration(@Nullable Project project) {
    Path configDir;
    if (project == null || project.isDefault()) {
      configDir = PathManager.getConfigDir().resolve(TEMPLATES_DIR);
    }
    else {
      configDir = ProjectKt.getStateStore(project).getProjectFilePath().getParent().resolve(TEMPLATES_DIR);
    }

    FileTemplateLoadResult result = loadDefaultTemplates(new ArrayList<>(MANAGER_TO_DIR.values()));
    Map<String, FTManager> managers = new HashMap<>(MANAGER_TO_DIR.size());
    for (Map.Entry<String, String> entry: MANAGER_TO_DIR.entrySet()) {
      String name = entry.getKey();
      String pathPrefix = entry.getValue();
      FTManager manager = new FTManager(name, configDir.resolve(pathPrefix), name.equals(FileTemplateManager.INTERNAL_TEMPLATES_CATEGORY));
      manager.setDefaultTemplates(result.result.get(pathPrefix));
      manager.loadCustomizedContent();
      managers.put(name, manager);
    }

    return new LoadedConfiguration(managers, result.defaultTemplateDescription, result.defaultIncludeDescription);
  }

  private static @NotNull FileTemplateLoadResult loadDefaultTemplates(@NotNull List<String> prefixes) {
    FileTemplateLoadResult result = new FileTemplateLoadResult(new MultiMap<>());
    Set<URL> processedUrls = new HashSet<>();
    Set<ClassLoader> processedLoaders = Collections.newSetFromMap(new IdentityHashMap<>());
    for (IdeaPluginDescriptorImpl plugin : PluginManagerCore.getPluginSet().enabledPlugins) {
      ClassLoader loader = plugin.getClassLoader();
      if (((loader instanceof PluginAwareClassLoader) && ((PluginAwareClassLoader)loader).getFiles().isEmpty()) ||
          !processedLoaders.add(loader)) {
        // test or development mode, when IDEA_CORE's loader contains all the classpath
        continue;
      }

      try {
        Enumeration<URL> resourceUrls;
        if (loader instanceof UrlClassLoader) {
          // don't use parents from plugin class loader - we process all plugins
          resourceUrls = ((UrlClassLoader)loader).getClassPath().getResources(DEFAULT_TEMPLATES_ROOT);
        }
        else {
          resourceUrls = loader.getResources(DEFAULT_TEMPLATES_ROOT);
        }

        while (resourceUrls.hasMoreElements()) {
          URL url = resourceUrls.nextElement();
          if (!processedUrls.add(url)) {
            continue;
          }

          String protocol = url.getProtocol();
          if (URLUtil.JAR_PROTOCOL.equalsIgnoreCase(protocol)) {
            List<String> children = UrlUtil.getChildPathsFromJar(url);
            if (!children.isEmpty()) {
              loadDefaultsFromRoot(path -> FileTemplateLoadResult.createSupplier(url, path), children, prefixes, result);
             }
          }
          else if (URLUtil.FILE_PROTOCOL.equalsIgnoreCase(protocol)) {
            FileTemplateLoadResult.processDirectory(url, result, prefixes);
          }
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return result;
  }

  private static void loadDefaultsFromRoot(@NotNull Function<String, Supplier<String>> dataProducer,
                                           @NotNull List<String> children,
                                           @NotNull List<String> prefixes,
                                           @NotNull FileTemplateLoadResult result) throws IOException {
    Set<String> descriptionPaths = new HashSet<>();
    for (String path : children) {
      if (path.equals("default.html")) {
        result.defaultTemplateDescription = dataProducer.fun(path);
      }
      else if (path.equals("includes/default.html")) {
        result.defaultIncludeDescription = dataProducer.fun(path);
      }
      else if (path.endsWith(DESCRIPTION_EXTENSION_SUFFIX)) {
        descriptionPaths.add(path);
      }
    }

    for (String path : children) {
      if (!path.endsWith(FTManager.TEMPLATE_EXTENSION_SUFFIX)) {
        continue;
      }

      for (String prefix : prefixes) {
        if (!matchesPrefix(path, prefix)) {
          continue;
        }

        String filename = path.substring(prefix.isEmpty() ? 0 : prefix.length() + 1, path.length() - FTManager.TEMPLATE_EXTENSION_SUFFIX.length());
        String extension = FileUtilRt.getExtension(filename);
        String templateName = filename.substring(0, filename.length() - extension.length() - 1);
        String descriptionPath = getDescriptionPath(prefix, templateName, extension, descriptionPaths);
        Supplier<String> descriptionSupplier = descriptionPath == null ? null : dataProducer.fun(descriptionPath);
        result.result.putValue(prefix, new DefaultTemplate(templateName, extension, dataProducer.fun(path), descriptionSupplier, descriptionPath));
        // FTManagers loop
        break;
      }
    }
  }

   static boolean matchesPrefix(@NotNull String path, @NotNull String prefix) {
    if (prefix.isEmpty()) {
      return path.indexOf('/') == -1;
    }
    return FileUtil.startsWith(path, prefix) && path.indexOf('/', prefix.length() + 1) == -1;
  }

  //Example: templateName="NewClass"   templateExtension="java"
  static @Nullable String getDescriptionPath(@NotNull String pathPrefix,
                                                     @NotNull String templateName,
                                                     @NotNull String templateExtension,
                                                     @NotNull Set<String> descriptionPaths) {
    final Locale locale = Locale.getDefault();

    String descName = MessageFormat
      .format("{0}.{1}_{2}_{3}" + DESCRIPTION_EXTENSION_SUFFIX, templateName, templateExtension,
              locale.getLanguage(), locale.getCountry());
    String descPath = pathPrefix.isEmpty() ? descName : pathPrefix + "/" + descName;
    if (descriptionPaths.contains(descPath)) {
      return descPath;
    }

    descName = MessageFormat.format("{0}.{1}_{2}" + DESCRIPTION_EXTENSION_SUFFIX, templateName, templateExtension, locale.getLanguage());
    descPath = pathPrefix.isEmpty() ? descName : pathPrefix + "/" + descName;
    if (descriptionPaths.contains(descPath)) {
      return descPath;
    }

    descName = templateName + "." + templateExtension + DESCRIPTION_EXTENSION_SUFFIX;
    descPath = pathPrefix.isEmpty() ? descName : pathPrefix + "/" + descName;
    if (descriptionPaths.contains(descPath)) {
      return descPath;
    }
    return null;
  }

  private static final class LoadedConfiguration {
    private final Supplier<String> defaultTemplateDescription;
    private final Supplier<String> defaultIncludeDescription;

    private final Map<String, FTManager> managers;

    private LoadedConfiguration(@NotNull Map<String, FTManager> managers,
                                Supplier<String> defaultTemplateDescription,
                                Supplier<String> defaultIncludeDescription) {

      this.managers = Collections.unmodifiableMap(managers);
      this.defaultTemplateDescription = defaultTemplateDescription;
      this.defaultIncludeDescription = defaultIncludeDescription;
    }

    private FTManager getManager(@NotNull String kind) {
      return managers.get(kind);
    }

    private Collection<FTManager> getManagers() {
      return managers.values();
    }
  }
}
