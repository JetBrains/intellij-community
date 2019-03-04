// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.util.SafeJdomFactory;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import org.jdom.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

final class LoadDescriptorsContext implements AutoCloseable {
  @NotNull
  private final ExecutorService myExecutorService;
  private final PluginLoadProgressManager myPluginLoadProgressManager;

  private final Collection<Interner<String>> myInterners;

  // synchronization will ruin parallel loading, so, string pool is local per thread
  private final ThreadLocal<SafeJdomFactory> myThreadLocalXmlFactory;

  LoadDescriptorsContext(@Nullable PluginLoadProgressManager pluginLoadProgressManager, boolean isParallel) {
    myPluginLoadProgressManager = pluginLoadProgressManager;
    int maxThreads = isParallel ? (Runtime.getRuntime().availableProcessors() - 1) : 1;
    if (maxThreads > 1) {
      myExecutorService = AppExecutorUtil.createBoundedApplicationPoolExecutor("PluginManager Loader", maxThreads);
      myInterners = Collections.newSetFromMap(ContainerUtil.newConcurrentMap(maxThreads));
    }
    else {
      myExecutorService = new SameThreadExecutorService();
      myInterners = new SmartList<>();
    }

    myThreadLocalXmlFactory = ThreadLocal.withInitial(() -> {
      PluginXmlFactory factory = new PluginXmlFactory();
      myInterners.add(factory.stringInterner);
      return factory;
    });
  }

  @NotNull
  ExecutorService getExecutorService() {
    return myExecutorService;
  }

  @Nullable
  PluginLoadProgressManager getPluginLoadProgressManager() {
    return myPluginLoadProgressManager;
  }

  @Nullable
  public SafeJdomFactory getXmlFactory() {
    return myThreadLocalXmlFactory.get();
  }

  @Override
  public void close() {
    if (myExecutorService instanceof SameThreadExecutorService) {
      myThreadLocalXmlFactory.remove();
      return;
    }

    myExecutorService.submit(() -> {
      for (Interner<String> interner : myInterners) {
        interner.clear();
      }
    });
    myExecutorService.shutdown();
  }

  /**
   * Consider using some threshold in StringInterner - CDATA is not interned at all,
   * but maybe some long text for Text node doesn't make sense to intern too.
   */
  // don't intern CDATA - in most cases it is used for some unique large text (e.g. plugin description)
  private final static class PluginXmlFactory extends SafeJdomFactory.BaseSafeJdomFactory {
    // doesn't make sense to intern class name since it is unique
    // ouch, do we really cannot agree how to name implementation class attribute?
    private static final List<String> CLASS_NAME_LIST = Arrays.asList(
      "implementation-class", "implementation",
      "serviceImplementation", "class", "className", "beanClass",
      "serviceInterface", "interface", "interfaceClass", "instance",
      "qualifiedName");

    private static final Set<String> CLASS_NAMES = ContainerUtil.newIdentityTroveSet(CLASS_NAME_LIST);

    private final Interner<String> stringInterner = new Interner<String>(ContainerUtil.concat(CLASS_NAME_LIST, IdeaPluginDescriptorImpl.SERVICE_QUALIFIED_ELEMENT_NAMES)) {
      @NotNull
      @Override
      public String intern(@NotNull String name) {
        // doesn't make any sense to intern long texts (JdomInternFactory doesn't intern CDATA, but plugin description can be simply Text)
        return name.length() < 64 ? super.intern(name) : name;
      }
    };

    @NotNull
    @Override
    public Interner<String> stringInterner() {
      return stringInterner;
    }

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

  // don't want to use Guava (MoreExecutors.newDirectExecutorService()) here
  private static final class SameThreadExecutorService extends AbstractExecutorService {
    private volatile boolean isTerminated;

    @Override
    public void shutdown() {
      isTerminated = true;
    }

    @Override
    public boolean isShutdown() {
      return isTerminated;
    }

    @Override
    public boolean isTerminated() {
      return isTerminated;
    }

    @Override
    public boolean awaitTermination(long theTimeout, @NotNull TimeUnit theUnit) {
      shutdown();
      return true;
    }

    @NotNull
    @Contract(pure = true)
    @Override
    public List<Runnable> shutdownNow() {
      return Collections.emptyList();
    }

    @Override
    public void execute(@NotNull Runnable command) {
      command.run();
    }
  }
}
