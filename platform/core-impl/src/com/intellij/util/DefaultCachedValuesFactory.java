// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class DefaultCachedValuesFactory implements CachedValuesFactory {
  private final Project myProject;

  DefaultCachedValuesFactory(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull <T> CachedValue<T> createCachedValue(@NotNull CachedValueProvider<T> provider, boolean trackValue) {
    return new CachedValueImpl<T>(provider, trackValue) {
      @Override
      public boolean isFromMyProject(@NotNull Project project) {
        return myProject == project;
      }
    };
  }

  @Override
  public @NotNull <T> CachedValue<T> createCachedValue(@NotNull UserDataHolder userDataHolder,
                                                       @NotNull CachedValueProvider<T> provider,
                                                       boolean trackValue) {
    return createCachedValue(provider, trackValue);
  }

  @Override
  public @NotNull <T, P> ParameterizedCachedValue<T, P> createParameterizedCachedValue(@NotNull ParameterizedCachedValueProvider<T, P> provider,
                                                                                       boolean trackValue) {
    return new ParameterizedCachedValueImpl<>(myProject, provider, trackValue);
  }

  @Override
  public @NotNull <T, P> ParameterizedCachedValue<T, P> createParameterizedCachedValue(@NotNull UserDataHolder userDataHolder,
                                                                                       @NotNull ParameterizedCachedValueProvider<T, P> provider,
                                                                                       boolean trackValue) {
    return createParameterizedCachedValue(provider, trackValue);
  }
}
