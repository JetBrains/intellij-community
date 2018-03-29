// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.execution.testDiscovery.indices.DiscoveredTestDataHolder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.PathKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

public class TestDiscoveryIndex implements Disposable {
  static final Logger LOG = Logger.getInstance(TestDiscoveryIndex.class);

  private volatile DiscoveredTestDataHolder myHolder;
  private final Object myLock = new Object();
  private final Path myBasePath;

  public static TestDiscoveryIndex getInstance(Project project) {
    return project.getComponent(TestDiscoveryIndex.class);
  }

  public TestDiscoveryIndex(Project project) {
    this(project, TestDiscoveryExtension.baseTestDiscoveryPathForProject(project));
  }

  public TestDiscoveryIndex(final Project project, @NotNull Path basePath) {
    myBasePath = basePath;

    if (Files.exists(basePath)) {
      StartupManager.getInstance(project).registerPostStartupActivity(() -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
        getHolder(); // proactively init with maybe io costly compact
      }));
    }
  }

  public boolean hasTestTrace(@NotNull String testClassName, @NotNull String testMethodName, byte frameworkId) {
    Boolean result = executeUnderLock(holder -> holder.hasTestTrace(testClassName, testMethodName, frameworkId));
    return result == Boolean.TRUE;
  }

  public void removeTestTrace(@NotNull String testClassName, @NotNull String testMethodName, byte frameworkId) {
    executeUnderLock(holder -> {
      holder.removeTestTrace(testClassName, testMethodName, frameworkId);
      return null;
    });
  }

  @NotNull
  public MultiMap<String, String> getTestsByClassName(@NotNull String classFQName, byte frameworkId) {
    MultiMap<String, String> map = executeUnderLock(holder -> holder.getTestsByClassName(classFQName, frameworkId));
    return map == null ? MultiMap.empty() : map;
  }

  @NotNull
  public MultiMap<String, String> getTestsByMethodName(@NotNull String classFQName, @NotNull String methodName, byte frameworkId) {
    MultiMap<String, String> map = executeUnderLock(holder -> holder.getTestsByMethodName(classFQName, methodName, frameworkId));
    return map == null ? MultiMap.empty() : map;
  }

  @NotNull
  public Collection<String> getTestModulesByMethodName(@NotNull String classFQName, @NotNull String methodName, byte frameworkId) {
    Collection<String> modules = executeUnderLock(holder -> holder.getTestModulesByMethodName(classFQName, methodName, frameworkId));
    return modules == null ? Collections.emptySet() : modules;
  }

  @Override
  public void dispose() {
    synchronized (myLock) {
      DiscoveredTestDataHolder holder = myHolder;
      if (holder != null) {
        holder.dispose();
        myHolder = null;
      }
    }
  }

  public void updateTestData(@NotNull String testClassName,
                             @NotNull String testMethodName,
                             @NotNull MultiMap<String, String> usedMethods,
                             @Nullable String moduleName,
                             byte frameworkId) {
    executeUnderLock(holder -> {
      holder.updateTestData(testClassName, testMethodName, usedMethods, moduleName, frameworkId);
      return null;
    });
  }

  private DiscoveredTestDataHolder getHolder() {
    DiscoveredTestDataHolder holder = myHolder;

    if (holder == null) {
      synchronized (myLock) {
        holder = myHolder;
        if (holder == null && myBasePath != null) {
          myHolder = holder = new DiscoveredTestDataHolder(myBasePath);
        }
      }
    }
    return holder;
  }

  private <R> R executeUnderLock(ThrowableConvertor<DiscoveredTestDataHolder, R, IOException> action) {
    synchronized (myLock) {
      DiscoveredTestDataHolder holder = getHolder();
      if (holder == null || holder.isDisposed()) return null;
      try {
        return action.convert(holder);
      }
      catch (Throwable throwable) {
        LOG.error("Unexpected problem", throwable);
        holder.dispose();
        PathKt.delete(myBasePath);
        myHolder = null;
      }
      return null;
    }
  }
}
