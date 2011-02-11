/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.projectRoots.ex;

import com.intellij.rt.compiler.JavacRunner;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.NonNls;

public class JavaSdkUtil {
  @NonNls public static final String IDEA_PREPEND_RTJAR = "idea.prepend.rtjar";

  public static void addRtJar(PathsList pathsList) {
    final String ideaRtJarPath = getIdeaRtJarPath();
    if (Boolean.getBoolean(IDEA_PREPEND_RTJAR)) {
      pathsList.addFirst(ideaRtJarPath);
    }
    else {
      pathsList.addTail(ideaRtJarPath);
    }
  }


  public static String getJunit4JarPath() {
    try {
      return PathUtil.getJarPathForClass(Class.forName("org.junit.Test"));
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public static String getJunit3JarPath() {
    try {
      return PathUtil.getJarPathForClass(Class.forName("junit.runner.TestSuiteLoader")); //junit3 specific class
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public static String getIdeaRtJarPath() {
    return PathUtil.getJarPathForClass(JavacRunner.class);
  }
}
