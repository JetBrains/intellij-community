/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.reference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
public class RefJavaModuleImpl extends RefElementImpl implements RefJavaModule {

  protected RefJavaModuleImpl(@NotNull String name, @NotNull RefElement owner) {
    super(name, owner);
  }

  protected RefJavaModuleImpl(@NotNull PsiFile file, @NotNull RefManager manager) {
    super(file, manager);
  }

  protected RefJavaModuleImpl(@NotNull String name,
                              @NotNull PsiElement element,
                              @NotNull RefManager manager) {
    super(name, element, manager);
  }

  @Override
  protected void initialize() {

  }

  @Nullable
  @Override
  public PsiJavaModule getElement() {
    return (PsiJavaModule)super.getElement();
  }
}
