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
package com.intellij.util;

import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class DefaultCachedValuesFactory implements CachedValuesFactory {
  private final Project myProject;

  public DefaultCachedValuesFactory(Project project) {
    myProject = project;
  }

  @Override
  public <T> CachedValue<T> createCachedValue(@NotNull CachedValueProvider<T> provider, boolean trackValue) {
    return trackValue ? new CachedValueImpl<T>(provider) {
      @Override
      protected Object[] getDependencies(CachedValueProvider.Result<T> result) {
        return getDependenciesPlusValue(result);
      }

      @Override
      public boolean isFromMyProject(Project project) {
        return myProject == project;
      }
    } : new CachedValueImpl<T>(provider) {

      @Override
      public boolean isFromMyProject(Project project) {
        return myProject == project;
      }
    };
  }

  @Override
  public <T, P> ParameterizedCachedValue<T, P> createParameterizedCachedValue(@NotNull ParameterizedCachedValueProvider<T, P> provider,
                                                                              boolean trackValue) {
    return trackValue ? new ParameterizedCachedValueImpl<T, P>(provider) {
      @Override
      public boolean isFromMyProject(Project project) {
        return myProject == project;
      }

      @Override
      protected Object[] getDependencies(CachedValueProvider.Result<T> tResult) {
        return getDependenciesPlusValue(tResult);
      }
    } : new ParameterizedCachedValueImpl<T, P>(provider) {
      @Override
      public boolean isFromMyProject(Project project) {
        return myProject == project;
      }
    };
  }
}
