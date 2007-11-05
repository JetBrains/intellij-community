/*
 * @author max
 */
package com.intellij.ide;

import com.intellij.util.lang.UrlClassLoader;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class Bootstrap {
  private static final String PLUGIN_MANAGER = "com.intellij.ide.plugins.PluginManager";

  private Bootstrap() {}

  public static void main(final String[] args, final String mainClass, final String methodName) {
    main(args, mainClass, methodName, new ArrayList<URL>());
  }

  public static void main(final String[] args, final String mainClass, final String methodName, List<URL> classpathElements) {
    UrlClassLoader newClassLoader = ClassloaderUtil.initClassloader(classpathElements);
    try {
      final Class klass = Class.forName(PLUGIN_MANAGER, true, newClassLoader);

      final Method startMethod = klass.getDeclaredMethod("start", String.class, String.class, String[].class);
      startMethod.setAccessible(true);
      startMethod.invoke(null, mainClass, methodName, args);
    }
    catch (Exception e) {
      e.printStackTrace(System.err);
    }
  }
}