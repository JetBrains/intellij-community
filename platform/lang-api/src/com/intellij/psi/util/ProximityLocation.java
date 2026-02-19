// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProximityLocation implements UserDataHolder {
  private final PsiElement myPosition;
  private final Module myPositionModule;
  private final ProcessingContext myContext;

  public ProximityLocation(final @Nullable PsiElement position, final Module positionModule) {
    this(position, positionModule, new ProcessingContext());
  }

  public ProximityLocation(PsiElement position, @Nullable Module positionModule, ProcessingContext context) {
    myPosition = position;
    myPositionModule = positionModule;
    myContext = context;
  }

  public @Nullable Module getPositionModule() {
    return myPositionModule;
  }

  public @Nullable PsiElement getPosition() {
    return myPosition;
  }

  public @Nullable Project getProject() {
    return myPosition != null ? myPosition.getProject() : null;
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
