// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.util.SafeJdomFactory;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import org.jdom.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutorService;

final class LoadDescriptorsContext implements AutoCloseable {
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
      myExecutorService = null;
      myInterners = new SmartList<>();
    }

    myThreadLocalXmlFactory = ThreadLocal.withInitial(() -> {
      Interner<String> interner = new Interner<String>(Arrays.asList(PluginXmlFactory.CLASS_NAMES)) {
        @NotNull
        @Override
        public String intern(@NotNull String name) {
          // doesn't make any sense to intern long texts (JdomInternFactory doesn't intern CDATA, but plugin description can be simply Text)
          return name.length() < 64 ? super.intern(name) : name;
        }
      };
      myInterners.add(interner);
      return new PluginXmlFactory(interner);
    });
  }

  @Nullable
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
    if (myExecutorService == null) {
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
    // ouch, do we really cannot agree how to name implementation class attribute?
    private static final String[] CLASS_NAMES = new String[]{
      "implementation-class", "implementation", "implementationClass", "serviceImplementation", "class", "className", "beanClass",
      "serviceInterface", "interface", "interfaceClass", "instance",
      "qualifiedName",
    };

    private final Interner<String> stringInterner;

    PluginXmlFactory(@NotNull Interner<String> stringInterner) {
      this.stringInterner = stringInterner;

      for (int i = 0; i < CLASS_NAMES.length; i++) {
        CLASS_NAMES[i] = stringInterner.intern(CLASS_NAMES[i]);
      }
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
      for (String s : CLASS_NAMES) {
        if (internedName == s) {
          return super.attribute(internedName, value, type, namespace);
        }
      }
      return super.attribute(internedName, stringInterner.intern(value), type, namespace);
    }

    @NotNull
    @Override
    public Text text(@NotNull String text, @NotNull Element parentElement) {
      if (parentElement.getName() == "className" || parentElement.getName() == "implementation-class") {
        return super.text(text, parentElement);
      }
      else {
        return super.text(stringInterner.intern(text), parentElement);
      }
    }
  }
}
