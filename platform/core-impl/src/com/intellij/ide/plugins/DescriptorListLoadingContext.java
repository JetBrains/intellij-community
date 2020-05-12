// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.SafeJdomFactory;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSetInterner;
import com.intellij.util.containers.Interner;
import org.jdom.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

final class DescriptorListLoadingContext implements AutoCloseable {
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private static final boolean unitTestWithBundledPlugins = Boolean.getBoolean("idea.run.tests.with.bundled.plugins");

  static final int IS_PARALLEL = 1;
  static final int IGNORE_MISSING_INCLUDE = 2;
  static final int IGNORE_MISSING_SUB_DESCRIPTOR = 4;
  static final int SKIP_DISABLED_PLUGINS = 8;
  static final int CHECK_OPTIONAL_CONFIG_NAME_UNIQUENESS = 16;

  private static final Logger LOG = PluginManagerCore.getLogger();

  private final @NotNull ExecutorService executorService;

  private final ConcurrentLinkedQueue<SafeJdomFactory[]> toDispose;

  // synchronization will ruin parallel loading, so, string pool is local per thread
  private final Supplier<PluginXmlFactory> xmlFactorySupplier;
  private final @Nullable ThreadLocal<PluginXmlFactory[]> threadLocalXmlFactory;
  private final int maxThreads;

  final @NotNull PluginLoadingResult result;

  final Set<PluginId> disabledPlugins;

  private volatile String defaultVersion;

  final boolean ignoreMissingInclude;
  final boolean ignoreMissingSubDescriptor;
  private final boolean skipDisabledPlugins;

  boolean usePluginClassLoader = !PluginManagerCore.isUnitTestMode || unitTestWithBundledPlugins;

  private final Map<String, PluginId> optionalConfigNames;

  String bundledPluginsPath = PathManager.getPreInstalledPluginsPath();
  boolean loadBundledPlugins = !PluginManagerCore.isUnitTestMode;

  public static @NotNull DescriptorListLoadingContext createSingleDescriptorContext(@NotNull Set<PluginId> disabledPlugins) {
    return new DescriptorListLoadingContext(IGNORE_MISSING_SUB_DESCRIPTOR, disabledPlugins, PluginManagerCore.createLoadingResult(null));
  }

  DescriptorListLoadingContext(int flags, @NotNull Set<PluginId> disabledPlugins, @NotNull PluginLoadingResult result) {
    this.result = result;
    this.disabledPlugins = disabledPlugins;
    ignoreMissingInclude = (flags & IGNORE_MISSING_INCLUDE) == IGNORE_MISSING_INCLUDE;
    ignoreMissingSubDescriptor = (flags & IGNORE_MISSING_SUB_DESCRIPTOR) == IGNORE_MISSING_SUB_DESCRIPTOR;
    skipDisabledPlugins = (flags & SKIP_DISABLED_PLUGINS) == SKIP_DISABLED_PLUGINS;
    optionalConfigNames = (flags & CHECK_OPTIONAL_CONFIG_NAME_UNIQUENESS) == CHECK_OPTIONAL_CONFIG_NAME_UNIQUENESS ? new ConcurrentHashMap<>() : null;

    maxThreads = (flags & IS_PARALLEL) == IS_PARALLEL ? (Runtime.getRuntime().availableProcessors() - 1) : 1;
    if (maxThreads > 1) {
      executorService = AppExecutorUtil.createBoundedApplicationPoolExecutor("PluginManager Loader", maxThreads, false);
      toDispose = new ConcurrentLinkedQueue<>();

      threadLocalXmlFactory = ThreadLocal.withInitial(() -> {
        PluginXmlFactory factory = new PluginXmlFactory();
        PluginXmlFactory[] ref = {factory};
        toDispose.add(ref);
        return ref;
      });
      xmlFactorySupplier = () -> threadLocalXmlFactory.get()[0];
    }
    else {
      executorService = ConcurrencyUtil.newSameThreadExecutorService();
      toDispose = null;
      threadLocalXmlFactory = null;
      PluginXmlFactory factory = new PluginXmlFactory();
      xmlFactorySupplier = () -> factory;
    }
  }

  boolean isPluginDisabled(@NotNull PluginId id) {
    return id != PluginManagerCore.CORE_ID && disabledPlugins.contains(id);
  }

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull Logger getLogger() {
    return LOG;
  }

  @NotNull ExecutorService getExecutorService() {
    return executorService;
  }

  @NotNull SafeJdomFactory getXmlFactory() {
    return xmlFactorySupplier.get();
  }

  @Override
  public void close() {
    if (threadLocalXmlFactory == null) {
      return;
    }

    if (maxThreads <= 1) {
      threadLocalXmlFactory.remove();
      return;
    }

    executorService.execute(() -> {
      for (SafeJdomFactory[] ref : toDispose) {
        ref[0] = null;
      }
    });
    executorService.shutdown();
  }

  public @NotNull Interner<String> getStringInterner() {
    return xmlFactorySupplier.get().stringInterner;
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
    return xmlFactorySupplier.get().releaseDateFormat;
  }

  public @NotNull List<String> getVisitedFiles() {
    return xmlFactorySupplier.get().visitedFiles;
  }

  boolean checkOptionalConfigShortName(@NotNull String configFile, @NotNull IdeaPluginDescriptor descriptor, @NotNull IdeaPluginDescriptor rootDescriptor) {
    PluginId pluginId = descriptor.getPluginId();
    if (pluginId == null) {
      return false;
    }

    Map<String, PluginId> configNames = this.optionalConfigNames;
    if (configNames == null) {
      return false;
    }

    PluginId oldPluginId = configNames.put(configFile, pluginId);
    if (oldPluginId == null || oldPluginId.equals(pluginId)) {
      return false;
    }

    getLogger().error("Optional config file with name '" + configFile + "' already registered by '" + oldPluginId +
                      "'. " +
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
  private static final List<String> CLASS_NAME_LIST = Arrays.asList(
    "implementation-class", "implementation",
    "serviceImplementation", "class", "className", "beanClass",
    "serviceInterface", "interface", "interfaceClass", "instance",
    "qualifiedName");

  private static final Set<String> CLASS_NAMES = ContainerUtil.newIdentityTroveSet(CLASS_NAME_LIST);

  final Interner<String> stringInterner = new HashSetInterner<String>(ContainerUtil.concat(CLASS_NAME_LIST,
                                                                                           Arrays.asList("id",
                                                                                                         PluginManagerCore.VENDOR_JETBRAINS,
                                                                                                         XmlReader.APPLICATION_SERVICE,
                                                                                                         XmlReader.PROJECT_SERVICE,
                                                                                                         XmlReader.MODULE_SERVICE))) {


    @Override
    public @NotNull String intern(@NotNull String name) {
      // doesn't make any sense to intern long texts (JdomInternFactory doesn't intern CDATA, but plugin description can be simply Text)
      return name.length() < 64 ? super.intern(name) : name;
    }
  };

  final DateFormat releaseDateFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
  final List<String> visitedFiles = new ArrayList<>(3);

  @Override
  public @NotNull Element element(@NotNull String name, @Nullable Namespace namespace) {
    return super.element(stringInterner.intern(name), namespace);
  }

  @Override
  public @NotNull Attribute attribute(@NotNull String name, @NotNull String value, @Nullable AttributeType type, @Nullable Namespace namespace) {
    String internedName = stringInterner.intern(name);
    if (CLASS_NAMES.contains(internedName)) {
      return super.attribute(internedName, value, type, namespace);
    }
    else {
      return super.attribute(internedName, stringInterner.intern(value), type, namespace);
    }
  }

  @Override
  public @NotNull Text text(@NotNull String text, @NotNull Element parentElement) {
    if (CLASS_NAMES.contains(parentElement.getName())) {
      return super.text(text, parentElement);
    }
    else {
      return super.text(stringInterner.intern(text), parentElement);
    }
  }
}
