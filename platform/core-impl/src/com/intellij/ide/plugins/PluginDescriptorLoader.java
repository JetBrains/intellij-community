// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SafeJdomFactory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.util.text.Strings;
import com.intellij.serialization.SerializationException;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.PlatformUtils;
import com.intellij.util.io.Decompressor;
import com.intellij.util.io.URLUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@ApiStatus.Internal
public final class PluginDescriptorLoader {
  @ApiStatus.Internal
  public static @Nullable IdeaPluginDescriptorImpl loadDescriptor(@NotNull Path file,
                                                                  boolean isBundled,
                                                                  @NotNull DescriptorListLoadingContext parentContext) {
    try (DescriptorLoadingContext context = new DescriptorLoadingContext(parentContext, isBundled, /* isEssential = */ false,
                                                                         PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER)) {
      return loadDescriptorFromFileOrDir(file, PluginManagerCore.PLUGIN_XML, context, Files.isDirectory(file));
    }
  }

  static @Nullable IdeaPluginDescriptorImpl loadDescriptorFromDir(@NotNull Path file,
                                                                  @NotNull String descriptorRelativePath,
                                                                  @Nullable Path pluginPath,
                                                                  @NotNull DescriptorLoadingContext context) {
    Path descriptorFile = file.resolve(descriptorRelativePath);
    try {
      IdeaPluginDescriptorImpl descriptor = new IdeaPluginDescriptorImpl(pluginPath == null ? file : pluginPath, descriptorFile.getParent(), context.isBundled);
      Element element = JDOMUtil.load(descriptorFile, context.parentContext.getXmlFactory());
      descriptor.readExternal(element, context.pathResolver, context.parentContext, descriptor);
      return descriptor;
    }
    catch (NoSuchFileException e) {
      return null;
    }
    catch (SerializationException | JDOMException | IOException e) {
      if (context.isEssential) {
        ExceptionUtilRt.rethrowUnchecked(e);
        throw new RuntimeException(e);
      }
      context.parentContext.result.reportCannotLoad(file, e);
    }
    catch (Throwable e) {
      if (context.isEssential) {
        ExceptionUtilRt.rethrowUnchecked(e);
        throw new RuntimeException(e);
      }
      DescriptorListLoadingContext.LOG.warn("Cannot load " + descriptorFile, e);
    }
    return null;
  }

  static @Nullable IdeaPluginDescriptorImpl loadDescriptorFromJar(@NotNull Path file,
                                                                  @NotNull String fileName,
                                                                  @NotNull PathBasedJdomXIncluder.PathResolver<?> pathResolver,
                                                                  @NotNull DescriptorLoadingContext context,
                                                                  @Nullable Path pluginPath) {
    SafeJdomFactory factory = context.parentContext.getXmlFactory();
    try {
      Path metaInf = context.open(file).getPath("/META-INF");
      Element element;
      try {
        element = JDOMUtil.load(metaInf.resolve(fileName), factory);
      }
      catch (NoSuchFileException ignore) {
        return null;
      }

      IdeaPluginDescriptorImpl descriptor = new IdeaPluginDescriptorImpl(pluginPath == null ? file : pluginPath, metaInf, context.isBundled);
      if (descriptor.readExternal(element, pathResolver, context.parentContext, descriptor)) {
        descriptor.jarFiles = Collections.singletonList(descriptor.getPluginPath());
      }
      return descriptor;
    }
    catch (SerializationException | InvalidDataException | JDOMException e) {
      if (context.isEssential) {
        ExceptionUtilRt.rethrowUnchecked(e);
        throw new RuntimeException(e);
      }
      context.parentContext.result.reportCannotLoad(file, e);
    }
    catch (Throwable e) {
      if (context.isEssential) {
        ExceptionUtilRt.rethrowUnchecked(e);
        throw new RuntimeException(e);
      }
      DescriptorListLoadingContext.LOG.info("Cannot load " + file + "!/META-INF/" + fileName, e);
    }

    return null;
  }

  static @Nullable IdeaPluginDescriptorImpl loadDescriptorFromFileOrDir(@NotNull Path file,
                                                                        @NotNull String pathName,
                                                                        @NotNull DescriptorLoadingContext context,
                                                                        boolean isDirectory) {
    if (isDirectory) {
      return loadDescriptorFromDirAndNormalize(file, pathName, context);
    }
    else if (StringUtilRt.endsWithIgnoreCase(file.getFileName().toString(), ".jar")) {
      return loadDescriptorFromJar(file, pathName, context.pathResolver, context, null);
    }
    else {
      return null;
    }
  }

  private static @Nullable IdeaPluginDescriptorImpl loadDescriptorFromDirAndNormalize(@NotNull Path file,
                                                                                      @NotNull String pathName,
                                                                                      @NotNull DescriptorLoadingContext context) {
    String descriptorRelativePath = PluginManagerCore.META_INF + pathName;
    IdeaPluginDescriptorImpl descriptor = loadDescriptorFromDir(file, descriptorRelativePath, null, context);
    if (descriptor != null) {
      return descriptor;
    }

    List<Path> pluginJarFiles = new ArrayList<>(), dirs = new ArrayList<>();
    if (!collectPluginDirectoryContents(file, pluginJarFiles, dirs)) return null;

    if (!pluginJarFiles.isEmpty()) {
      PluginXmlPathResolver pathResolver = new PluginXmlPathResolver(pluginJarFiles, context);
      for (Path jarFile : pluginJarFiles) {
        descriptor = loadDescriptorFromJar(jarFile, pathName, pathResolver, context, file);
        if (descriptor != null) {
          descriptor.jarFiles = pluginJarFiles;
          return descriptor;
        }
      }
    }

    for (Path dir : dirs) {
      IdeaPluginDescriptorImpl otherDescriptor = loadDescriptorFromDir(dir, descriptorRelativePath, file, context);
      if (otherDescriptor != null) {
        if (descriptor != null) {
          //noinspection SpellCheckingInspection
          DescriptorListLoadingContext.LOG.info("Cannot load " + file + " because two or more plugin.xml's detected");
          return null;
        }
        descriptor = otherDescriptor;
      }
    }

    return descriptor;
  }

  static boolean collectPluginDirectoryContents(@NotNull Path file, @NotNull List<Path> pluginJarFiles, @NotNull List<? super Path> dirs) {
    try (DirectoryStream<Path> s = Files.newDirectoryStream(file.resolve("lib"))) {
      for (Path childFile : s) {
        if (Files.isDirectory(childFile)) {
          dirs.add(childFile);
        }
        else {
          String path = childFile.toString();
          if (StringUtilRt.endsWithIgnoreCase(path, ".jar") || StringUtilRt.endsWithIgnoreCase(path, ".zip")) {
            pluginJarFiles.add(childFile);
          }
        }
      }
    }
    catch (IOException e) {
      return false;
    }
    if (!pluginJarFiles.isEmpty()) {
      putMoreLikelyPluginJarsFirst(file, pluginJarFiles);
    }
    return true;
  }

  /*
   * Sort the files heuristically to load the plugin jar containing plugin descriptors without extra ZipFile accesses
   * File name preference:
   * a) last order for files with resources in name, like resources_en.jar
   * b) last order for files that have -digit suffix is the name e.g. completion-ranking.jar is before gson-2.8.0.jar or junit-m5.jar
   * c) jar with name close to plugin's directory name, e.g. kotlin-XXX.jar is before all-open-XXX.jar
   * d) shorter name, e.g. android.jar is before android-base-common.jar
   */
  private static void putMoreLikelyPluginJarsFirst(@NotNull Path pluginDir, @NotNull List<? extends Path> filesInLibUnderPluginDir) {
    String pluginDirName = pluginDir.getFileName().toString();

    filesInLibUnderPluginDir.sort((o1, o2) -> {
      String o2Name = o2.getFileName().toString();
      String o1Name = o1.getFileName().toString();

      boolean o2StartsWithResources = o2Name.startsWith("resources");
      boolean o1StartsWithResources = o1Name.startsWith("resources");
      if (o2StartsWithResources != o1StartsWithResources) {
        return o2StartsWithResources ? -1 : 1;
      }

      boolean o2IsVersioned = fileNameIsLikeVersionedLibraryName(o2Name);
      boolean o1IsVersioned = fileNameIsLikeVersionedLibraryName(o1Name);
      if (o2IsVersioned != o1IsVersioned) {
        return o2IsVersioned ? -1 : 1;
      }

      boolean o2StartsWithNeededName = StringUtilRt.startsWithIgnoreCase(o2Name, pluginDirName);
      boolean o1StartsWithNeededName = StringUtilRt.startsWithIgnoreCase(o1Name, pluginDirName);
      if (o2StartsWithNeededName != o1StartsWithNeededName) {
        return o2StartsWithNeededName ? 1 : -1;
      }

      return o1Name.length() - o2Name.length();
    });
  }

  private static boolean fileNameIsLikeVersionedLibraryName(@NotNull String name) {
    int i = name.lastIndexOf('-');
    if (i == -1) return false;
    if (i + 1 < name.length()) {
      char c = name.charAt(i + 1);
      if (Character.isDigit(c)) return true;
      return (c == 'm' || c == 'M') && i + 2 < name.length() && Character.isDigit(name.charAt(i + 2));
    }
    return false;
  }

  private static void loadDescriptorsFromDir(@NotNull Path dir,
                                             boolean isBundled,
                                             @NotNull DescriptorListLoadingContext context) throws ExecutionException, InterruptedException {
    List<Future<IdeaPluginDescriptorImpl>> tasks = new ArrayList<>();
    ExecutorService executorService = context.getExecutorService();
    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir)) {
      for (Path file : dirStream) {
        tasks.add(executorService.submit(() -> loadDescriptor(file, isBundled, context)));
      }
    }
    catch (IOException ignore) {
      return;
    }

    for (Future<IdeaPluginDescriptorImpl> task : tasks) {
      IdeaPluginDescriptorImpl descriptor = task.get();
      if (descriptor != null) {
        context.result.add(descriptor, /* overrideUseIfCompatible = */ false);
      }
    }
  }

  private static @Nullable IdeaPluginDescriptorImpl loadDescriptorFromResource(@NotNull URL resource, @NotNull String pathName, @NotNull DescriptorLoadingContext loadingContext) {
    try {
      Path file;
      if (URLUtil.FILE_PROTOCOL.equals(resource.getProtocol())) {
        file = Paths.get(Strings.trimEnd(UrlClassLoader.urlToFilePath(resource.getPath()).replace('\\', '/'), pathName)).getParent();
        return loadDescriptorFromFileOrDir(file, pathName, loadingContext, Files.isDirectory(file));
      }
      else if (URLUtil.JAR_PROTOCOL.equals(resource.getProtocol())) {
        file = Paths.get(UrlClassLoader.urlToFilePath(resource.getPath()));
        Path parentFile = file.getParent();
        if (parentFile == null || !parentFile.endsWith("lib")) {
          return loadDescriptorFromJar(file, pathName, loadingContext.pathResolver, loadingContext, null);
        }
        else {
          // Support for unpacked plugins in classpath. E.g. .../community/build/dependencies/build/kotlin/Kotlin/lib/kotlin-plugin.jar
          IdeaPluginDescriptorImpl descriptor = loadDescriptorFromJar(file, pathName, loadingContext.pathResolver, loadingContext, file.getParent().getParent());
          if (descriptor != null) {
            descriptor.jarFiles = null;
          }
          return descriptor;
        }
      }
      else {
        return null;
      }
    }
    catch (Throwable e) {
      if (loadingContext.isEssential) {
        ExceptionUtilRt.rethrowUnchecked(e);
        throw new RuntimeException(e);
      }
      DescriptorListLoadingContext.LOG.info("Cannot load " + resource, e);
      return null;
    }
    finally {
      loadingContext.close();
    }
  }

  private static void loadDescriptorsFromProperty(@NotNull PluginLoadingResult result,
                                                  @NotNull DescriptorListLoadingContext context) {
    String pathProperty = System.getProperty(PluginManagerCore.PROPERTY_PLUGIN_PATH);
    if (pathProperty == null) {
      return;
    }

    // gradle-intellij-plugin heavily depends on this property in order to have core class loader plugins during tests
    boolean useCoreClassLoaderForPluginsFromProperty =
      Boolean.parseBoolean(System.getProperty("idea.use.core.classloader.for.plugin.path"));

    for (StringTokenizer t = new StringTokenizer(pathProperty, File.pathSeparatorChar + ","); t.hasMoreTokens(); ) {
      String s = t.nextToken();
      IdeaPluginDescriptorImpl descriptor = loadDescriptor(Paths.get(s), false, context);
      if (descriptor != null) {
        // plugins added via property shouldn't be overridden to avoid plugin root detection issues when running external plugin tests
        result.add(descriptor,  /* overrideUseIfCompatible = */ true);
        if (useCoreClassLoaderForPluginsFromProperty) {
          descriptor.setUseCoreClassLoader();
        }
      }
    }
  }

  static @NotNull DescriptorListLoadingContext loadDescriptors() {
    int flags = DescriptorListLoadingContext.IS_PARALLEL | DescriptorListLoadingContext.IGNORE_MISSING_SUB_DESCRIPTOR;
    boolean isUnitTestMode = PluginManagerCore.isUnitTestMode;
    if (isUnitTestMode) {
      flags |= DescriptorListLoadingContext.IGNORE_MISSING_INCLUDE;
    }
    if (isUnitTestMode || PluginManagerCore.isRunningFromSources()) {
      flags |= DescriptorListLoadingContext.CHECK_OPTIONAL_CONFIG_NAME_UNIQUENESS;
    }

    PluginLoadingResult result = PluginManagerCore.createLoadingResult(null);
    Path bundledPluginPath;
    if (isUnitTestMode) {
      bundledPluginPath = null;
    }
    else if (Boolean.getBoolean("idea.use.dev.build.server")) {
      bundledPluginPath = Paths.get(PathManager.getHomePath(), "out/dev-run", PlatformUtils.getPlatformPrefix(), "plugins");
    }
    else {
      bundledPluginPath = Paths.get(PathManager.getPreInstalledPluginsPath());
    }

    DescriptorListLoadingContext context = new DescriptorListLoadingContext(flags, DisabledPluginsState.disabledPlugins(), result);
    try {
      loadBundledDescriptorsAndDescriptorsFromDir(context, Paths.get(PathManager.getPluginsPath()), bundledPluginPath);

      loadDescriptorsFromProperty(result, context);

      if (isUnitTestMode && result.enabledPluginCount() <= 1) {
        // we're running in unit test mode, but the classpath doesn't contain any plugins; try to load bundled plugins anyway
        context.usePluginClassLoader = true;
        loadDescriptorsFromDir(Paths.get(PathManager.getPreInstalledPluginsPath()), /* isBundled = */ true, context);
      }
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
    finally {
      context.close();
    }

    context.result.finishLoading();
    return context;
  }

  static void loadBundledDescriptorsAndDescriptorsFromDir(@NotNull DescriptorListLoadingContext context,
                                                          @NotNull Path customPluginDir,
                                                          @Nullable Path bundledPluginDir)
    throws ExecutionException, InterruptedException {
    ClassLoader classLoader = PluginManagerCore.class.getClassLoader();
    Map<URL, String> urlsFromClassPath = new LinkedHashMap<>();
    URL platformPluginURL = computePlatformPluginUrlAndCollectPluginUrls(classLoader, urlsFromClassPath);
    try (DescriptorLoadingContext loadingContext = new DescriptorLoadingContext(context, /* isBundled = */ true, /* isEssential, doesn't matter = */ true,
                                                                                new ClassPathXmlPathResolver(classLoader))) {
      loadDescriptorsFromClassPath(urlsFromClassPath, loadingContext, platformPluginURL);
    }

    loadDescriptorsFromDir(customPluginDir, /* isBundled = */ false, context);

    if (bundledPluginDir != null) {
      loadDescriptorsFromDir(bundledPluginDir, /* isBundled = */ true, context);
    }
  }

  static void loadDescriptorsFromClassPath(@NotNull Map<URL, String> urls,
                                           @NotNull DescriptorLoadingContext context,
                                           @Nullable URL platformPluginURL) throws ExecutionException, InterruptedException {
    if (urls.isEmpty()) {
      return;
    }

    List<Future<IdeaPluginDescriptorImpl>> tasks = new ArrayList<>(urls.size());
    ExecutorService executorService = context.parentContext.getExecutorService();
    for (Map.Entry<URL, String> entry : urls.entrySet()) {
      URL url = entry.getKey();
      tasks.add(executorService.submit(() -> loadDescriptorFromResource(url, entry.getValue(), context.copy(url.equals(platformPluginURL)))));
    }

    PluginLoadingResult result = context.parentContext.result;
    for (Future<IdeaPluginDescriptorImpl> task : tasks) {
      IdeaPluginDescriptorImpl descriptor = task.get();
      if (descriptor != null) {
        if (!PluginManagerCore.usePluginClassLoader) {
          descriptor.setUseCoreClassLoader();
        }
        result.add(descriptor, /* overrideUseIfCompatible = */ false);
      }
    }
  }

  private static @Nullable URL computePlatformPluginUrlAndCollectPluginUrls(@NotNull ClassLoader loader, @NotNull Map<URL, String> urls) {
    String platformPrefix = System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY);
    URL result = null;
    if (platformPrefix != null) {
      // should be the only plugin in lib (only for Ultimate and WebStorm for now)
      if ((platformPrefix.equals(PlatformUtils.IDEA_PREFIX) || platformPrefix.equals(PlatformUtils.WEB_PREFIX)) && !PluginManagerCore.isRunningFromSources()) {
        urls.put(loader.getResource(PluginManagerCore.PLUGIN_XML_PATH), PluginManagerCore.PLUGIN_XML);
        return null;
      }

      String fileName = platformPrefix + "Plugin.xml";
      URL resource = loader.getResource(PluginManagerCore.META_INF + fileName);
      if (resource != null) {
        urls.put(resource, fileName);
        result = resource;
      }
    }
    collectPluginFilesInClassPath(loader, urls);
    return result;
  }

  static void collectPluginFilesInClassPath(@NotNull ClassLoader loader, @NotNull Map<URL, String> urls) {
    try {
      Enumeration<URL> enumeration = loader.getResources(PluginManagerCore.PLUGIN_XML_PATH);
      while (enumeration.hasMoreElements()) {
        urls.put(enumeration.nextElement(), PluginManagerCore.PLUGIN_XML);
      }
    }
    catch (IOException e) {
      PluginManagerCore.getLogger().info(e);
    }
  }

  /**
   * Think twice before use and get approve from core team.
   *
   * Returns enabled plugins only.
   */
  @ApiStatus.Internal
  public static @NotNull List<IdeaPluginDescriptorImpl> loadUncachedDescriptors() {
    return loadDescriptors().result.getEnabledPlugins();
  }

  public static @Nullable IdeaPluginDescriptorImpl loadDescriptorFromArtifact(@NotNull Path file, @Nullable BuildNumber buildNumber) throws IOException {
    DescriptorListLoadingContext parentContext = new DescriptorListLoadingContext(DescriptorListLoadingContext.IGNORE_MISSING_SUB_DESCRIPTOR,
                                                                                  DisabledPluginsState.disabledPlugins(),
                                                                                  PluginManagerCore.createLoadingResult(buildNumber));
    Path outputDir = null;
    try (DescriptorLoadingContext context = new DescriptorLoadingContext(parentContext, false, false, PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER)) {
      IdeaPluginDescriptorImpl descriptor = loadDescriptorFromFileOrDir(file, PluginManagerCore.PLUGIN_XML, context, false);
      if (descriptor != null || !file.toString().endsWith(".zip")) {
        return descriptor;
      }

      outputDir = Files.createTempDirectory("plugin");
      new Decompressor.Zip(file).extract(outputDir);
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputDir)) {
        Iterator<Path> iterator = stream.iterator();
        if (iterator.hasNext()) {
          descriptor = loadDescriptorFromFileOrDir(iterator.next(), PluginManagerCore.PLUGIN_XML, context, true);
        }
      }
      catch (NoSuchFileException ignore) {
      }
      return descriptor;
    }
    finally {
      if (outputDir != null) {
        FileUtil.delete(outputDir);
      }
    }
  }


  public static @Nullable IdeaPluginDescriptorImpl tryLoadFullDescriptor(@NotNull IdeaPluginDescriptorImpl descriptor) {
    return isFull(descriptor) ?
           descriptor :
           PluginManager.loadDescriptor(descriptor.getPluginPath(),
                                        Collections.emptySet(),
                                        descriptor.isBundled(),
                                        createPathResolverForPlugin(descriptor, null));
  }

  static @NotNull PathBasedJdomXIncluder.PathResolver<?> createPathResolverForPlugin(@NotNull IdeaPluginDescriptorImpl descriptor,
                                                                                     @Nullable DescriptorLoadingContext context) {
    if (PluginManagerCore.isRunningFromSources() &&
        descriptor.getPluginPath().getFileSystem().equals(FileSystems.getDefault()) &&
        descriptor.getPluginPath().toString().contains("out/classes")) {
      return new ClassPathXmlPathResolver(descriptor.getPluginClassLoader());
    }

    if (context != null) {
      PathBasedJdomXIncluder.PathResolver<Path> resolver = createPluginJarsPathResolver(descriptor.getPluginPath(), context);
      if (resolver != null) {
        return resolver;
      }
    }
    return PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER;
  }

  public static @NotNull IdeaPluginDescriptorImpl loadFullDescriptor(@NotNull IdeaPluginDescriptorImpl descriptor) {
    // PluginDescriptor fields are cleaned after the plugin is loaded, so we need to reload the descriptor to check if it's dynamic
    IdeaPluginDescriptorImpl fullDescriptor = tryLoadFullDescriptor(descriptor);
    if (fullDescriptor == null) {
      PluginManagerCore.getLogger().error("Could not load full descriptor for plugin " + descriptor.getPluginPath());
      return descriptor;
    }
    else {
      return fullDescriptor;
    }
  }

  private static boolean isFull(@NotNull IdeaPluginDescriptorImpl descriptor) {
    return !PluginManagerCore.hasDescriptorByIdentity(descriptor) ||
           PluginManagerCore.getLoadedPlugins().contains(descriptor);
  }

  private static @Nullable PathBasedJdomXIncluder.PathResolver<Path> createPluginJarsPathResolver(@NotNull Path pluginDir,
                                                                                                  @NotNull DescriptorLoadingContext context) {
    List<Path> pluginJarFiles = new ArrayList<>();
    List<Path> dirs = new ArrayList<>();
    if (!collectPluginDirectoryContents(pluginDir, pluginJarFiles, dirs)) {
      return null;
    }
    return new PluginXmlPathResolver(pluginJarFiles, context);
  }
}
