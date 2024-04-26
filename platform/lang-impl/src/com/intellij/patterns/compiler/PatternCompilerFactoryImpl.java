// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns.compiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ConcurrentFactoryMap;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Gregory.Shrago
 */
final class PatternCompilerFactoryImpl extends PatternCompilerFactory {
  private static final ExtensionPointName<PatternClassBean> PATTERN_CLASS_EP =
    new ExtensionPointName<>("com.intellij.patterns.patternClass");

  private final Map<List<Class<?>>, PatternCompiler<?>> compilers = new ConcurrentHashMap<>();

  private final Map<String, Class[]> myClasses = ConcurrentFactoryMap.createMap(key -> {
    List<Class<?>> result = new ArrayList<>(1);
    List<String> typeList = key == null ? null : Arrays.asList(key.split(",|\\s"));
    PATTERN_CLASS_EP.processWithPluginDescriptor((bean, descriptor) -> {
      if (typeList == null || typeList.contains(bean.getAlias())) {
        try {
          result.add(ApplicationManager.getApplication().loadClass(bean.className, descriptor));
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          Logger.getInstance(PatternCompilerFactoryImpl.class).error(e);
        }
      }
      return Unit.INSTANCE;
    });
    return result.isEmpty() ? ArrayUtilRt.EMPTY_CLASS_ARRAY : result.toArray(ArrayUtilRt.EMPTY_CLASS_ARRAY);
  });

  PatternCompilerFactoryImpl() {
    PATTERN_CLASS_EP.addChangeListener(this::dropCache, null);
  }

  @Override
  public Class<?> @NotNull [] getPatternClasses(String alias) {
    return myClasses.get(alias);
  }

  @Override
  public @NotNull <T> PatternCompiler<T> getPatternCompiler(Class @NotNull [] patternClasses) {
    //noinspection unchecked
    return (PatternCompiler<T>)compilers.computeIfAbsent(Arrays.asList(patternClasses), PatternCompilerImpl::new);
  }

  @Override
  public void dropCache() {
    myClasses.clear();
    compilers.clear();
  }
}
