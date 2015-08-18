/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

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
    return PathUtil.getJarPathForClass(ReflectionUtil.forName("org.junit.Test"));
  }

  public static String getJunit3JarPath() {
    return PathUtil.getJarPathForClass(ReflectionUtil.forName("junit.runner.TestSuiteLoader")); //junit3 specific class
  }

  public static String getIdeaRtJarPath() {
    return PathUtil.getJarPathForClass(JavacRunner.class);
  }

  @NotNull
  public static List<String> getJUnit4JarPaths() {
    return Arrays.asList(getJunit4JarPath(),
                         PathUtil.getJarPathForClass(ReflectionUtil.forName("org.hamcrest.Matcher")),
                         PathUtil.getJarPathForClass(ReflectionUtil.forName("org.hamcrest.Matchers")));
  }
}
