// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cucumber;

import com.intellij.TestCaseLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.bucketing.HashingBucketingScheme;
import com.intellij.util.lang.UrlClassLoader;
import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.io.MultiLoader;
import cucumber.runtime.io.Resource;
import cucumber.runtime.io.ResourceLoaderClassFinder;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CucumberMain {
  private static final Logger LOG = Logger.getInstance(CucumberMain.class);

  static {
    // Radar #5755208: Command line Java applications need a way to launch without a Dock icon.
    System.setProperty("apple.awt.UIElement", "true");
  }

  public static void main(String[] args) {
    int exitStatus;
    try {
      ClassLoader original = Thread.currentThread().getContextClassLoader();
      List<Path> files = new ArrayList<>();
      for (String path : System.getProperty("java.class.path").split(File.pathSeparator)) {
        if (!path.isEmpty()) {
          files.add(Path.of(path));
        }
      }
      UrlClassLoader loader = UrlClassLoader.build().files(files).parent(original.getParent())
        .useCache()
        .usePersistentClasspathIndexForLocalClassDirectories()
        .get();
      Thread.currentThread().setContextClassLoader(loader);
      exitStatus = (Integer)loader.loadClass(CucumberMain.class.getName())
        .getMethod("run", String[].class, ClassLoader.class)
        .invoke(null, args, loader);
    }
    catch (InvocationTargetException e) {
      LOG.warn(e);
      var targetException = e.getTargetException();
      if (targetException instanceof AssertionError) {
        exitStatus = 0;
      }
      else {
        exitStatus = 1;
      }
    }
    catch (Throwable e) {
      LOG.warn(e);
      exitStatus = 1;
    }
    System.exit(exitStatus);
  }

  public static int run(String[] argv, ClassLoader classLoader) {
    Throwable throwable = null;
    Runtime runtime = null;

    try {
      RuntimeOptions runtimeOptions = new RuntimeOptions(new ArrayList<>(Arrays.asList(argv)));
      MultiLoader resourceLoader = new MultiLoader(classLoader) {
        @Override
        public Iterable<Resource> resources(String path, String suffix) {
          Iterable<Resource> resources = super.resources(path, suffix);
          if (!TestCaseLoader.shouldBucketTests() || !".feature".equals(suffix)) {
            return resources;
          }

          List<Resource> result = new ArrayList<>();
          for (Resource resource : resources) {
            if (HashingBucketingScheme.matchesCurrentBucketViaHashing(resource.getPath())) {
              result.add(resource);
            }
          }
          return result;
        }
      };
      ResourceLoaderClassFinder classFinder = new ResourceLoaderClassFinder(resourceLoader, classLoader);
      runtime = new Runtime(resourceLoader, classFinder, classLoader, runtimeOptions);
      runtime.run();
    }
    catch (Throwable t) {
      throwable = t;
      Logger.getInstance(CucumberMain.class).error(t);
    }

    if (throwable != null) {
      LOG.error(throwable);
    }

    if (runtime != null) {
      for (Throwable error : runtime.getErrors()) {
        LOG.error(error);
      }
    }

    return throwable != null ? 1 : 0;
  }
}
