// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

final class DescriptorListLoadingContext implements AutoCloseable {
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  final static boolean unitTestWithBundledPlugins = Boolean.getBoolean("idea.run.tests.with.bundled.plugins");

  static final int IS_PARALLEL = 1;
  static final int IGNORE_MISSING_INCLUDE = 2;
  static final int SKIP_DISABLED_PLUGINS = 4;

  private static final Logger LOG = PluginManagerCore.getLogger();

  @NotNull
  private final ExecutorService executorService;

  private final ConcurrentLinkedQueue<SafeJdomFactory[]> toDispose;

  // synchronization will ruin parallel loading, so, string pool is local per thread
  private final Supplier<PluginXmlFactory> xmlFactorySupplier;
  @Nullable
  private final ThreadLocal<PluginXmlFactory[]> threadLocalXmlFactory;
  private final int maxThreads;

  @NotNull
  final PluginLoadingResult result;

  final Set<PluginId> disabledPlugins;

  private volatile String defaultVersion;

  final boolean ignoreMissingInclude;
  final boolean skipDisabledPlugins;

  // enable when unit tests will be added
  @SuppressWarnings("FieldMayBeStatic")
  final boolean readConditionalConfigDirectlyIfPossible = false;

  boolean usePluginClassLoader = !PluginManagerCore.isUnitTestMode || unitTestWithBundledPlugins;

  @NotNull
  public static DescriptorListLoadingContext createSingleDescriptorContext(@NotNull Set<PluginId> disabledPlugins) {
    return new DescriptorListLoadingContext(0, disabledPlugins, new PluginLoadingResult(Collections.emptyMap(), PluginManagerCore.getBuildNumber()));
  }

  DescriptorListLoadingContext(int flags, @NotNull Set<PluginId> disabledPlugins, @NotNull PluginLoadingResult result) {
    this.result = result;
    this.disabledPlugins = disabledPlugins;
    ignoreMissingInclude = (flags & IGNORE_MISSING_INCLUDE) == IGNORE_MISSING_INCLUDE;
    skipDisabledPlugins = (flags & SKIP_DISABLED_PLUGINS) == SKIP_DISABLED_PLUGINS;

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

  @NotNull Logger getLogger() {
    return LOG;
  }

  @NotNull
  ExecutorService getExecutorService() {
    return executorService;
  }

  @NotNull
  SafeJdomFactory getXmlFactory() {
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

  @NotNull
  public Interner<String> getStringInterner() {
    return xmlFactorySupplier.get().stringInterner;
  }

  @NotNull
  public String getDefaultVersion() {
    String result = defaultVersion;
    if (result == null) {
      result = this.result.productBuildNumber.asStringWithoutProductCode();
      defaultVersion = result;
    }
    return result;
  }

  @NotNull
  public DateFormat getDateParser() {
    return xmlFactorySupplier.get().releaseDateFormat;
  }

  @NotNull
  public List<String> getVisitedFiles() {
    return xmlFactorySupplier.get().visitedFiles;
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
                                                                                                         IdeaPluginDescriptorImpl.APPLICATION_SERVICE,
                                                                                                         IdeaPluginDescriptorImpl.PROJECT_SERVICE,
                                                                                                         IdeaPluginDescriptorImpl.MODULE_SERVICE))) {
    @NotNull
    @Override
    public String intern(@NotNull String name) {
      // doesn't make any sense to intern long texts (JdomInternFactory doesn't intern CDATA, but plugin description can be simply Text)
      return name.length() < 64 ? super.intern(name) : name;
    }
  };

  final DateFormat releaseDateFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
  final List<String> visitedFiles = new ArrayList<>(3);

  @NotNull
  @Override
  public Element element(@NotNull String name, @Nullable Namespace namespace) {
    return super.element(stringInterner.intern(name), namespace);
  }

  @NotNull
  @Override
  public Attribute attribute(@NotNull String name, @NotNull String value, @Nullable AttributeType type, @Nullable Namespace namespace) {
    String internedName = stringInterner.intern(name);
    if (CLASS_NAMES.contains(internedName)) {
      return super.attribute(internedName, value, type, namespace);
    }
    else {
      return super.attribute(internedName, stringInterner.intern(value), type, namespace);
    }
  }

  @NotNull
  @Override
  public Text text(@NotNull String text, @NotNull Element parentElement) {
    if (CLASS_NAMES.contains(parentElement.getName())) {
      return super.text(text, parentElement);
    }
    else {
      return super.text(stringInterner.intern(text), parentElement);
    }
  }
}
