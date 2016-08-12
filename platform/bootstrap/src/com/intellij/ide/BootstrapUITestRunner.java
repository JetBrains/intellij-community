/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide;

import com.intellij.openapi.application.PathManager;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

public class BootstrapUITestRunner {

  private static final String UITESTS_ROOT = "plugins/android/ui-tests-dir";
  private static final String ANDROID_ROOT = "plugins/android/lib";

  private static final String RUNNER_CLASS = "com.android.tools.idea.tests.gui.framework.PlainJUnitRunnable";
  private static final String SUITE_CLASS = "com.android.tools.idea.tests.gui.GuiSanityTestSuite";

  private static String getTestClasspath() {
    String ideaHome = PathManager.getHomePath();

    StringBuilder classpath = new StringBuilder();
    // We have to load UI tests together with the android module classpath.
    classpath.append(new File(ideaHome, UITESTS_ROOT).getAbsolutePath());

    for(File entry : new File(ideaHome, ANDROID_ROOT).listFiles()) {
      if (entry.isFile() && entry.getName().endsWith(".jar")) {
        classpath.append(File.pathSeparator);
        classpath.append(entry.getAbsolutePath());
      }
    }
    return classpath.toString();
  }

  public static void main(String[] args) throws Exception {
    System.setProperty("idea.additional.classpath", getTestClasspath());
    ClassLoader newClassLoader = BootstrapClassLoaderUtil.initClassLoader(true);

    Class<?> suiteClass = Class.forName(SUITE_CLASS, true, newClassLoader);

    Runnable runner = (Runnable) Class.forName(RUNNER_CLASS, true, newClassLoader).getConstructor(Class.class).newInstance(suiteClass);
    runner.run();
  }
}
