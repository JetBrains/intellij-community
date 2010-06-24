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
package com.intellij.psi.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class ProximityLocation implements UserDataHolder {
  private final PsiElement myPosition;
  private final Module myPositionModule;
  private final ProcessingContext myContext;

  public ProximityLocation(@NotNull final PsiElement position, @NotNull final Module positionModule) {
    this(position, positionModule, new ProcessingContext());
  }

  public ProximityLocation(PsiElement position, Module positionModule, ProcessingContext context) {
    myPosition = position;
    myPositionModule = positionModule;
    myContext = context;
  }

  public Module getPositionModule() {
    return myPositionModule;
  }

  @NotNull
  public PsiElement getPosition() {
    return myPosition;
  }

  public Project getProject() {
    return myPosition.getProject();
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myContext.get(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myContext.put(key, value);
  }
}
