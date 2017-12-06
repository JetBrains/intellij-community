/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.javac;


import javax.tools.StandardJavaFileManager;
import java.io.File;
import java.lang.reflect.Method;

/**
 * @author Eugene Zhuravlev
 */
public class OptimizedFileManagerUtil {

  private static class OptimizedFileManagerClassHolder {
    static final String CLASS_NAME = "org.jetbrains.jps.javac.OptimizedFileManager";
    static final Class<StandardJavaFileManager> managerClass;
    static final Method directoryCacheClearMethod;
    static final String initError;
    static {
      Class<StandardJavaFileManager> aClass = null;
      Method cacheClearMethod = null;
      String error = null;
      try {
        @SuppressWarnings("unchecked")
        Class<StandardJavaFileManager> c = (Class<StandardJavaFileManager>)Class.forName(CLASS_NAME);
        aClass = c;
        try {
          cacheClearMethod = c.getMethod("fileGenerated", File.class);
          cacheClearMethod.setAccessible(true);
        }
        catch (NoSuchMethodException e) {
          //LOG.info(e);
        }
      }
      catch (Throwable ex) {
        aClass = null;
        error = ex.getClass().getName() + ": " + ex.getMessage();
      }
      managerClass = aClass;
      directoryCacheClearMethod = cacheClearMethod;
      initError = error;
    }

    private OptimizedFileManagerClassHolder() {
    }
  }

  private static class OptimizedFileManager17ClassHolder {
    static final String CLASS_NAME = "org.jetbrains.jps.javac.OptimizedFileManager17";
    static final Class<StandardJavaFileManager> managerClass;
    static final Method directoryCacheClearMethod;
    static final String initError;
    static {
      Class<StandardJavaFileManager> aClass;
      Method cacheClearMethod = null;
      String error = null;
      try {
        @SuppressWarnings("unchecked")
        Class<StandardJavaFileManager> c = (Class<StandardJavaFileManager>)Class.forName(CLASS_NAME);
        aClass = c;
        try {
          cacheClearMethod = c.getMethod("fileGenerated", File.class);
          cacheClearMethod.setAccessible(true);
        }
        catch (NoSuchMethodException e) {
          //LOG.info(e);
        }
      }
      catch (Throwable ex) {
        aClass = null;
        error = ex.getClass().getName() + ": " + ex.getMessage();
      }
      managerClass = aClass;
      directoryCacheClearMethod = cacheClearMethod;
      initError = error;
    }

    private OptimizedFileManager17ClassHolder() {
    }
  }

  public static Class<StandardJavaFileManager> getManagerClass() {
    final Class<StandardJavaFileManager> aClass = OptimizedFileManagerClassHolder.managerClass;
    if (aClass != null) {
      return aClass;
    }
    return OptimizedFileManager17ClassHolder.managerClass;
  }

  public static Method getCacheClearMethod() {
    final Method method = OptimizedFileManagerClassHolder.directoryCacheClearMethod;
    if (method != null) {
      return method;
    }
    return OptimizedFileManager17ClassHolder.directoryCacheClearMethod;
  }

  public static String getLoadError() {
    StringBuilder builder = new StringBuilder();
    if (OptimizedFileManagerClassHolder.initError != null) {
      builder.append(OptimizedFileManagerClassHolder.initError);
    }
    if (OptimizedFileManager17ClassHolder.initError != null) {
      if (builder.length() > 0) {
        builder.append("\n");
      }
      builder.append(OptimizedFileManager17ClassHolder.initError);
    }
    return builder.toString();
  }

}
