// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.execution.testDiscovery.indices.DiscoveredTestDataHolder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Couple;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.PathKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
public final class TestDiscoveryIndex implements Disposable {
  static final Logger LOG = Logger.getInstance(TestDiscoveryIndex.class);

  private volatile DiscoveredTestDataHolder myHolder;
  private final Object myLock = new Object();
  private final Path basePath;

  public static TestDiscoveryIndex getInstance(@NotNull Project project) {
    return project.getService(TestDiscoveryIndex.class);
  }

  @SuppressWarnings("unused")
  TestDiscoveryIndex(@NotNull Project project) {
    this(TestDiscoveryExtension.baseTestDiscoveryPathForProject(project));
  }

  @NonInjectable
  public TestDiscoveryIndex(@NotNull Path basePath) {
    this.basePath = basePath;
  }

  final static class MyPostStartUpActivity implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
        return;
      }

      NonUrgentExecutor.getInstance().execute(() -> {
        TestDiscoveryIndex service = getInstance(project);
        if (!Files.exists(service.basePath)) {
          return;
        }

        // proactively init with maybe io costly compact
        service.getHolder();
      });
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
  public MultiMap<String, String> getTestsByFile(String relativePath, byte frameworkId) {
    MultiMap<String, String> map = executeUnderLock(holder -> holder.getTestsByFile(relativePath, frameworkId));
    return map == null ? MultiMap.empty() : map;
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

  @NotNull
  public Collection<String> getAffectedFiles(Couple<String> testQName, byte frameworkId) {
    Collection<String> files = executeUnderLock(holder -> holder.getAffectedFiles(testQName, frameworkId));
    return files == null ? Collections.emptySet() : files;
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
                             @NotNull List<String> usedFiles,
                             @Nullable String moduleName,
                             byte frameworkId) {
    executeUnderLock(holder -> {
      holder.updateTestData(testClassName, testMethodName, usedMethods, usedFiles, moduleName, frameworkId);
      return null;
    });
  }

  private DiscoveredTestDataHolder getHolder() {
    DiscoveredTestDataHolder holder = myHolder;

    if (holder == null) {
      synchronized (myLock) {
        holder = myHolder;
        if (holder == null && basePath != null) {
          myHolder = holder = new DiscoveredTestDataHolder(basePath);
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
        PathKt.delete(basePath);
        myHolder = null;
      }
      return null;
    }
  }
}
