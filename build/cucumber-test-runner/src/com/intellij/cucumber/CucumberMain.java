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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.testFramework.TestRunnerUtil;
import com.intellij.util.ui.UIUtil;
import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.io.MultiLoader;
import cucumber.runtime.io.ResourceLoaderClassFinder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Dennis.Ushakov
 */
public class CucumberMain {
  static {
    // Radar #5755208: Command line Java applications need a way to launch without a Dock icon.
    System.setProperty("apple.awt.UIElement", "true");
  }

  public static void main(String[] args) throws IOException {
    int exitStatus;
    try {
      exitStatus = run(args, Thread.currentThread().getContextClassLoader());
    }
    catch (Throwable e) {
      exitStatus = 1;
    }
    System.exit(exitStatus);

  }

  public static int run(final String[] argv, final ClassLoader classLoader) throws IOException {
    final Ref<Throwable> errorRef = new Ref<>();
    final Ref<Runtime> runtimeRef = new Ref<>();

    try {
      TestRunnerUtil.replaceIdeEventQueueSafely();
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          try {
            RuntimeOptions runtimeOptions = new RuntimeOptions(new ArrayList(Arrays.asList(argv)));
            MultiLoader resourceLoader = new MultiLoader(classLoader);
            ResourceLoaderClassFinder classFinder = new ResourceLoaderClassFinder(resourceLoader, classLoader);
            Runtime runtime = new Runtime(resourceLoader, classFinder, classLoader, runtimeOptions);
            runtimeRef.set(runtime);
            runtime.run();
          }
          catch (Throwable throwable) {
            errorRef.set(throwable);
            Logger.getInstance(CucumberMain.class).error(throwable);
          }
        }
      });
    }
    catch (Throwable t) {
      errorRef.set(t);
      Logger.getInstance(CucumberMain.class).error(t);
    }

    final Throwable throwable = errorRef.get();
    if (throwable != null) {
      throwable.printStackTrace();
    }
    System.err.println("Failed tests :");
    for (Throwable error : runtimeRef.get().getErrors()) {
      error.printStackTrace();
      System.err.println("=============================");
    }
    return throwable != null ? 1 : 0;
  }
}
