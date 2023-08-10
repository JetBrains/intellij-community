// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

@ApiStatus.Internal
public final class PluginUtilImpl implements PluginUtil {
  private static final Logger LOG = Logger.getInstance(PluginUtilImpl.class);

  @Override
  public @Nullable PluginId getCallerPlugin(int stackFrameCount) {
    Class<?> aClass = ReflectionUtil.getCallerClass(stackFrameCount + 1);
    if (aClass == null) {
      return null;
    }
    ClassLoader classLoader = aClass.getClassLoader();
    return classLoader instanceof PluginAwareClassLoader ? ((PluginAwareClassLoader)classLoader).getPluginId() : null;
  }

  @Override
  public @Nullable PluginId findPluginId(@NotNull Throwable t) {
    return doFindPluginId(t);
  }

  public static @Nullable PluginId doFindPluginId(@NotNull Throwable t) {
    if (t instanceof PluginException) {
      return ((PluginException)t).getPluginId();
    }

    PluginId bundledId = null;
    Set<String> visitedClassNames = new HashSet<>();
    for (StackTraceElement element : t.getStackTrace()) {
      if (element != null) {
        String className = element.getClassName();
        if (visitedClassNames.add(className)) {
          PluginDescriptor descriptor = PluginManagerCore.getPluginDescriptorOrPlatformByClassName(className);
          PluginId id = descriptor == null ? null : descriptor.getPluginId();
          if (id != null && !PluginManagerCore.CORE_ID.equals(id)) {
            if (descriptor.isBundled()) {
              if (bundledId == null) {
                bundledId = id;
                logPluginDetection(className, id);
              }
            }
            else {
              logPluginDetection(className, id);
              return id;
            }
          }
        }
      }
    }

    if (t instanceof NoSuchMethodException) {
      // check is method called from plugin classes
      if (t.getMessage() != null) {
        StringBuilder className = new StringBuilder();
        StringTokenizer tok = new StringTokenizer(t.getMessage(), ".");
        while (tok.hasMoreTokens()) {
          String token = tok.nextToken();
          if (!token.isEmpty() && Character.isJavaIdentifierStart(token.charAt(0))) {
            className.append(token);
          }
        }

        PluginId pluginId = PluginManager.getPluginByClassNameAsNoAccessToClass(className.toString());
        if (pluginId != null) {
          return pluginId;
        }
      }
    }
    else if (t instanceof ClassNotFoundException) {
      // check is class from plugin classes
      if (t.getMessage() != null) {
        PluginId id = PluginManager.getPluginByClassNameAsNoAccessToClass(t.getMessage());
        if (id != null) {
          return id;
        }
      }
    }
    else if (t instanceof NoClassDefFoundError && t.getMessage() != null) {
      String className = StringUtil.substringAfterLast(t.getMessage(), " ");
      if (className == null) className = t.getMessage();
      if (className.indexOf('/') > 0) {
        className = className.replace('/', '.');
      }

      PluginId id = PluginManager.getPluginByClassNameAsNoAccessToClass(className);
      if (id != null) {
        return PluginManager.getPluginByClassNameAsNoAccessToClass(className);
      }
    }
    else if (t instanceof AbstractMethodError && t.getMessage() != null) {
      String s = t.getMessage();
      int pos = s.indexOf('(');
      if (pos >= 0) {
        s = s.substring(0, pos);
        pos = s.lastIndexOf('.');
        if (pos >= 0) {
          s = s.substring(0, pos);
          PluginId id = PluginManager.getPluginByClassNameAsNoAccessToClass(s);
          if (id != null) {
            return id;
          }
        }
      }
    }

    Throwable cause = t.getCause();
    PluginId causeId = cause == null ? null : doFindPluginId(cause);
    return causeId != null ? causeId : bundledId;
  }

  private static void logPluginDetection(String className, PluginId id) {
    if (LOG.isDebugEnabled()) {
      String message = "Detected a plugin " + id + " by class " + className;
      IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(id);
      if (descriptor != null) {
        ClassLoader loader = descriptor.getClassLoader();
        message += "; loader=" + loader + '/' + loader.getClass();
        if (loader instanceof PluginClassLoader) {
          message += "; loaded class: " + ((PluginClassLoader)loader).hasLoadedClass(className);
        }
      }
      LOG.debug(message);
    }
  }
}
