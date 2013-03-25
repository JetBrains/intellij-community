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

import com.intellij.idea.IdeaTestApplication;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import cucumber.io.MultiLoader;
import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeOptions;

/**
 * @author Dennis.Ushakov
 */
public class CucumberMain {
  static {
    // Radar #5755208: Command line Java applications need a way to launch without a Dock icon.
    System.setProperty("apple.awt.UIElement", "true");
  }

  public static void main(final String[] args) {
    IdeaTestApplication.getInstance(null);
    final Ref<Throwable> errorRef = new Ref<Throwable>();
    final Ref<Runtime> runtimeRef = new Ref<Runtime>();
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        try {
          final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
          RuntimeOptions runtimeOptions = new RuntimeOptions(System.getProperties(), args);
          cucumber.runtime.Runtime runtime = new Runtime(new MultiLoader(classLoader), classLoader, runtimeOptions);
          runtimeRef.set(runtime);
          runtime.writeStepdefsJson();
          runtime.run();
        } catch (Throwable throwable) {
          errorRef.set(throwable);
          Logger.getInstance(CucumberMain.class).error(throwable);
        }
      }
    }, ApplicationManager.getApplication().getDefaultModalityState());
    final Throwable throwable = errorRef.get();
    if (throwable != null) {
      throwable.printStackTrace();
    }
    System.err.println("Failed tests :");
    for (Throwable error : runtimeRef.get().getErrors()) {
      error.printStackTrace();
      System.err.println("=============================");
    }
    System.exit(throwable != null ? 1 : 0);
  }
}
