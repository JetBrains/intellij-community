/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cucumber;

import com.intellij.TestCaseLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.testFramework.TestRunnerUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.ui.UIUtil;
import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.io.MultiLoader;
import cucumber.runtime.io.Resource;
import cucumber.runtime.io.ResourceLoaderClassFinder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class CucumberMain {
  private static final Logger LOG = Logger.getInstance(CucumberMain.class);
  static {
    // Radar #5755208: Command line Java applications need a way to launch without a Dock icon.
    System.setProperty("apple.awt.UIElement", "true");
  }

  public static void main(String[] args) {
    int exitStatus;
    try {
      ClassLoader original = Thread.currentThread().getContextClassLoader();
      List<@NotNull URL> urls = ContainerUtil.mapNotNull(System.getProperty("java.class.path").split(File.pathSeparator), CucumberMain::fileToEncodedURL);
      UrlClassLoader loader = UrlClassLoader.build().urls(
        urls).parent(original.getParent()).allowLock().useCache()
        .usePersistentClasspathIndexForLocalClassDirectories()
        .useLazyClassloadingCaches(Boolean.parseBoolean(System.getProperty("idea.lazy.classloading.caches", "false")))
        .autoAssignUrlsWithProtectionDomain().get();
      Thread.currentThread().setContextClassLoader(loader);
      exitStatus = (Integer)loader.loadClass(CucumberMain.class.getName()).getMethod("run", String[].class, ClassLoader.class).invoke(null, args, loader);
    }
    catch (Throwable e) {
      LOG.warn(e);
      exitStatus = 1;
    }
    System.exit(exitStatus);
  }

  public static URL fileToEncodedURL(String path) {
    try {
      return new File(path).toURI().toURL();
    } catch (MalformedURLException e) {
      return null;
    }
  }


  public static int run(final String[] argv, final ClassLoader classLoader) {
    final Ref<Throwable> errorRef = new Ref<>();
    final Ref<Runtime> runtimeRef = new Ref<>();

    try {
      TestRunnerUtil.replaceIdeEventQueueSafely();
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
        try {
          RuntimeOptions runtimeOptions = new RuntimeOptions(new ArrayList<>(Arrays.asList(argv)));
          MultiLoader resourceLoader = new MultiLoader(classLoader) {
            @Override
            public Iterable<Resource> resources(String path, String suffix) {
              Iterable<Resource> resources = super.resources(path, suffix);
              if (TestCaseLoader.shouldBucketTests() && ".feature".equals(suffix)) {
                List<Resource> filteredResource = new ArrayList<>();
                resources.forEach(it -> {
                  if (TestCaseLoader.matchesCurrentBucket(it.getPath())) {
                    filteredResource.add(it);
                  }
                });
                return filteredResource;
              }
              return resources;
            }
          };
          ResourceLoaderClassFinder classFinder = new ResourceLoaderClassFinder(resourceLoader, classLoader);
          Runtime runtime = new Runtime(resourceLoader, classFinder, classLoader, runtimeOptions);
          runtimeRef.set(runtime);
          runtime.run();
        }
        catch (Throwable throwable) {
          errorRef.set(throwable);
          Logger.getInstance(CucumberMain.class).error(throwable);
        }
      });
    }
    catch (Throwable t) {
      errorRef.set(t);
      Logger.getInstance(CucumberMain.class).error(t);
    }

    final Throwable throwable = errorRef.get();
    if (throwable != null) {
      LOG.error(throwable);
    }
    for (Throwable error : runtimeRef.get().getErrors()) {
      LOG.error(error);
    }
    return throwable != null ? 1 : 0;
  }
}
