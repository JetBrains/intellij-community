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
package com.intellij.codeInsight.navigation.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface GotoDeclarationHandler {
  ExtensionPointName<GotoDeclarationHandler> EP_NAME = ExtensionPointName.create("com.intellij.gotoDeclarationHandler");

  /**
   * Provides an array of target declarations for given {@code sourceElement}.
   *
   * @param sourceElement input PSI element
   * @param offset        offset in the file
   * @param editor        @return all target declarations as an array of  {@code PsiElement} or null if none was found
   */
  @Nullable
  PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor);

  /**
   * Provides the custom action text.
   *
   * @param context the action data context
   * @return the custom text or null to use the default text
   */
  @Nullable
  String getActionText(DataContext context);
}
