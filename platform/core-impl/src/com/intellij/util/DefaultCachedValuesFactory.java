// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class DefaultCachedValuesFactory implements CachedValuesFactory {
  private final Project myProject;

  DefaultCachedValuesFactory(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public <T> CachedValue<T> createCachedValue(@NotNull CachedValueProvider<T> provider, boolean trackValue) {
    return new CachedValueImpl<T>(provider, trackValue) {
      @Override
      public boolean isFromMyProject(@NotNull Project project) {
        return myProject == project;
      }
    };
  }

  @NotNull
  @Override
  public <T, P> ParameterizedCachedValue<T, P> createParameterizedCachedValue(@NotNull ParameterizedCachedValueProvider<T, P> provider,
                                                                              boolean trackValue) {
    return new ParameterizedCachedValueImpl<>(myProject, provider, trackValue);
  }
}
