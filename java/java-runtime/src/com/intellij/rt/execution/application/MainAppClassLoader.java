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
package com.intellij.rt.execution.application;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author ven
 */
public class MainAppClassLoader  extends URLClassLoader {
  /**
   * @noinspection HardCodedStringLiteral
   */
  private static final String USER_CLASSPATH = "idea.user.classpath";
  private final Class myAppMainClass;

  private static URL[] makeUrls() {
    List classpath = new ArrayList();
    try {
      String userClassPath = System.getProperty(USER_CLASSPATH, "");
      StringTokenizer tokenizer = new StringTokenizer(userClassPath, File.pathSeparator, false);
      while (tokenizer.hasMoreTokens()) {
        String pathItem = tokenizer.nextToken();
        classpath.add(new File(pathItem).toURL());
      }
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }
    return (URL[]) classpath.toArray(new URL[classpath.size()]);
  }

  public MainAppClassLoader(ClassLoader loader) {
    super(makeUrls(), null);
    myAppMainClass = AppMain.class;
  }

  protected synchronized Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
    if (name.equals(myAppMainClass.getName())) {
      return myAppMainClass;
    }
    return super.loadClass(name, resolve);
  }
}
