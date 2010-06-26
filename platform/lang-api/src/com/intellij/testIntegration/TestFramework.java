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

package com.intellij.testIntegration;

import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface TestFramework {
  ExtensionPointName<TestFramework> EXTENSION_NAME = ExtensionPointName.create("com.intellij.testFramework");

  @NotNull
  String getName();

  @NotNull
  Icon getIcon();

  boolean isLibraryAttached(@NotNull Module module);

  @NotNull
  String getLibraryPath();

  @Nullable
  String getDefaultSuperClass();

  boolean isTestClass(@NotNull PsiElement clazz);

  boolean isPotentialTestClass(@NotNull PsiElement clazz);

  @Nullable
  PsiElement findSetUpMethod(@NotNull PsiElement clazz);

  @Nullable
  PsiElement findTearDownMethod(@NotNull PsiElement clazz);

  @Nullable
  PsiElement findOrCreateSetUpMethod(@NotNull PsiElement clazz) throws IncorrectOperationException;

  FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor();

  FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor();

  FileTemplateDescriptor getTestMethodFileTemplateDescriptor();
}
