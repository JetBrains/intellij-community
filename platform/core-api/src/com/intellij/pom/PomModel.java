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
package com.intellij.pom;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.pom.event.PomModelListener;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface PomModel extends UserDataHolder {
  <T extends PomModelAspect> T getModelAspect(@NotNull Class<T> aClass);

  void registerAspect(@NotNull Class<? extends PomModelAspect> aClass,
                      @NotNull PomModelAspect aspect,
                      @NotNull Set<PomModelAspect> dependencies);

  void addModelListener(@NotNull PomModelListener listener);
  void addModelListener(@NotNull PomModelListener listener, @NotNull Disposable parentDisposable);
  void removeModelListener(@NotNull PomModelListener listener);

  void runTransaction(@NotNull PomTransaction transaction) throws IncorrectOperationException;
}