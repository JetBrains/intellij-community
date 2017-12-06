/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * User: anna
  */
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class EntryPointsManager implements Disposable {
  public static EntryPointsManager getInstance(Project project) {
    return ServiceManager.getService(project, EntryPointsManager.class);
  }

  public abstract void resolveEntryPoints(@NotNull RefManager manager);

  public abstract void addEntryPoint(@NotNull RefElement newEntryPoint, boolean isPersistent);

  public abstract void removeEntryPoint(@NotNull RefElement anEntryPoint);

  @NotNull
  public abstract RefElement[] getEntryPoints();

  public abstract void cleanup();

  public abstract boolean isAddNonJavaEntries();

  public abstract void configureAnnotations();

  /**
   * {@link com.intellij.codeInspection.ex.EntryPointsManagerImpl#createConfigureAnnotationsButton()} should be used instead
   */
  @Deprecated
  public abstract JButton createConfigureAnnotationsBtn();

  public abstract boolean isEntryPoint(@NotNull PsiElement element);

  /**
   * Returns {@code true} for fields, annotated with "write" annotations
   */
  public abstract boolean isImplicitWrite(PsiElement element);
}
