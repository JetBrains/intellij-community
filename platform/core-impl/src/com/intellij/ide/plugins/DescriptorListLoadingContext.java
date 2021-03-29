// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.SafeJdomFactory;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jdom.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.ProviderNotFoundException;
import java.nio.file.spi.FileSystemProvider;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

final class DescriptorListLoadingContext implements AutoCloseable {
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private static final boolean unitTestWithBundledPlugins = Boolean.getBoolean("idea.run.tests.with.bundled.plugins");

  static final int IGNORE_MISSING_INCLUDE = 2;
  static final int IGNORE_MISSING_SUB_DESCRIPTOR = 4;
  static final int CHECK_OPTIONAL_CONFIG_NAME_UNIQUENESS = 8;

  static final Logger LOG = PluginManagerCore.getLogger();

  private final ConcurrentLinkedQueue<SafeJdomFactory[]> toDispose;

  private final ThreadLocal<PluginXmlFactory[]> threadLocalXmlFactory;

  final @NotNull PluginLoadingResult result;

  final Set<PluginId> disabledPlugins;

  private volatile String defaultVersion;

  final boolean ignoreMissingInclude;
  final boolean ignoreMissingSubDescriptor;

  boolean usePluginClassLoader = !PluginManagerCore.isUnitTestMode || unitTestWithBundledPlugins;

  private final Map<String, PluginId> optionalConfigNames;

  private FileSystemProvider zipFsProvider;

  public static @NotNull DescriptorListLoadingContext createSingleDescriptorContext(@NotNull Set<PluginId> disabledPlugins) {
    return new DescriptorListLoadingContext(IGNORE_MISSING_SUB_DESCRIPTOR, disabledPlugins, PluginManagerCore.createLoadingResult(null));
  }

  DescriptorListLoadingContext(int flags, @NotNull Set<PluginId> disabledPlugins, @NotNull PluginLoadingResult result) {
    this.result = result;
    this.disabledPlugins = disabledPlugins;
    ignoreMissingInclude = (flags & IGNORE_MISSING_INCLUDE) == IGNORE_MISSING_INCLUDE;
    ignoreMissingSubDescriptor = (flags & IGNORE_MISSING_SUB_DESCRIPTOR) == IGNORE_MISSING_SUB_DESCRIPTOR;
    optionalConfigNames = (flags & CHECK_OPTIONAL_CONFIG_NAME_UNIQUENESS) == CHECK_OPTIONAL_CONFIG_NAME_UNIQUENESS ? new ConcurrentHashMap<>() : null;

    toDispose = new ConcurrentLinkedQueue<>();

    // synchronization will ruin parallel loading, so, string pool is local for thread
    threadLocalXmlFactory = ThreadLocal.withInitial(() -> {
      PluginXmlFactory factory = new PluginXmlFactory();
      PluginXmlFactory[] ref = {factory};
      toDispose.add(ref);
      return ref;
    });
  }

  FileSystemProvider getZipFsProvider() {
    FileSystemProvider result = zipFsProvider;
    if (result == null) {
      result = findZipFsProvider();
      zipFsProvider = result;
    }
    return result;
  }

  private static @NotNull FileSystemProvider findZipFsProvider() {
    for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
      try {
        if (provider.getScheme().equals("jar")) {
          return provider;
        }
      }
      catch (UnsupportedOperationException ignored) {
      }
    }
    throw new ProviderNotFoundException("Provider not found");
  }

  boolean isPluginDisabled(@NotNull PluginId id) {
    return id != PluginManagerCore.CORE_ID && disabledPlugins.contains(id);
  }

  @NotNull SafeJdomFactory getXmlFactory() {
    return threadLocalXmlFactory.get()[0];
  }

  @Override
  public void close() {
    for (SafeJdomFactory[] ref : toDispose) {
      ref[0] = null;
    }
  }

  public @NotNull String internString(@NotNull String string) {
    return threadLocalXmlFactory.get()[0].intern(string);
  }

  public @NotNull String getDefaultVersion() {
    String result = defaultVersion;
    if (result == null) {
      result = this.result.productBuildNumber.get().asStringWithoutProductCode();
      defaultVersion = result;
    }
    return result;
  }

  public @NotNull DateFormat getDateParser() {
    return threadLocalXmlFactory.get()[0].releaseDateFormat;
  }

  public @NotNull List<String> getVisitedFiles() {
    return threadLocalXmlFactory.get()[0].visitedFiles;
  }

  boolean checkOptionalConfigShortName(@NotNull String configFile,
                                       @NotNull IdeaPluginDescriptor descriptor,
                                       @NotNull IdeaPluginDescriptor rootDescriptor) {
    PluginId pluginId = descriptor.getPluginId();
    if (pluginId == null) {
      return false;
    }

    Map<String, PluginId> configNames = optionalConfigNames;
    if (configNames == null || configFile.startsWith("intellij.")) {
      return false;
    }

    PluginId oldPluginId = configNames.put(configFile, pluginId);
    if (oldPluginId == null || oldPluginId.equals(pluginId)) {
      return false;
    }

    LOG.error("Optional config file with name '" + configFile + "' already registered by '" + oldPluginId + "'. " +
              "Please rename to ensure that lookup in the classloader by short name returns correct optional config. " +
              "Current plugin: '" + rootDescriptor + "'. ");
    return true;
  }
}

/**
 * Consider using some threshold in StringInterner (CDATA is not interned at all),
 * but maybe some long text for Text node doesn't make sense to intern too.
 */
// don't intern CDATA because in most cases it is used for some unique large text (e.g. plugin description)
final class PluginXmlFactory extends SafeJdomFactory.BaseSafeJdomFactory {
  // doesn't make sense to intern class name since it is unique
  // ouch, do we really cannot agree how to name implementation class attribute?
  private static final @NonNls Set<String> CLASS_NAMES = new ReferenceOpenHashSet<>(new String[]{
    "implementation", "implementationClass", "builderClass",
    "serviceImplementation", "class", "className", "beanClass",
    "serviceInterface", "interface", "interfaceClass", "instance", "implementation-class",
    "qualifiedName"});

  private static final List<String> EXTRA_STRINGS = Arrays.asList("id",
                                                                  PluginManagerCore.VENDOR_JETBRAINS,
                                                                  XmlReader.APPLICATION_SERVICE,
                                                                  XmlReader.PROJECT_SERVICE,
                                                                  XmlReader.MODULE_SERVICE);
  @SuppressWarnings("SSBasedInspection")
  private final ObjectOpenHashSet<String> strings = new ObjectOpenHashSet<>(CLASS_NAMES.size() + EXTRA_STRINGS.size());

  final DateFormat releaseDateFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
  final List<String> visitedFiles = new ArrayList<>(3);

  PluginXmlFactory() {
    strings.addAll(CLASS_NAMES);
    strings.addAll(EXTRA_STRINGS);
  }

  @NotNull String intern(@NotNull String string) {
    // doesn't make any sense to intern long texts (JdomInternFactory doesn't intern CDATA, but plugin description can be simply Text)
    return string.length() < 64 ? strings.addOrGet(string) : string;
  }

  @Override
  public @NotNull Element element(@NotNull String name, @Nullable Namespace namespace) {
    return super.element(intern(name), namespace);
  }

  @Override
  public @NotNull Attribute attribute(@NotNull String name, @NotNull String value, @Nullable AttributeType type, @Nullable Namespace namespace) {
    String internedName = intern(name);
    if (CLASS_NAMES.contains(internedName)) {
      return super.attribute(internedName, value, type, namespace);
    }
    else {
      return super.attribute(internedName, intern(value), type, namespace);
    }
  }

  @Override
  public @NotNull Text text(@NotNull String text, @NotNull Element parentElement) {
    return new Text(CLASS_NAMES.contains(parentElement.getName()) ? text : intern(text));
  }
}
