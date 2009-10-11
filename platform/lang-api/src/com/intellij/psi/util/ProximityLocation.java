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
