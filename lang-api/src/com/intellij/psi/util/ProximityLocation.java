/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class ProximityLocation {
  private final PsiElement myPosition;
  private final Module myPositionModule;

  public ProximityLocation(@NotNull final PsiElement position, @NotNull final Module positionModule) {
    myPosition = position;
    myPositionModule = positionModule;
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
}
