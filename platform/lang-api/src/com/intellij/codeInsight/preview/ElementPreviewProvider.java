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
package com.intellij.codeInsight.preview;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Point;

<<<<<<< HEAD:platform/lang-api/src/com/intellij/codeInsight/preview/ElementPreviewProvider.java
public interface ElementPreviewProvider {
  ExtensionPointName<ElementPreviewProvider> EP_NAME = ExtensionPointName.create("com.intellij.elementPreviewProvider");

  boolean isSupportedFile(@NotNull PsiFile psiFile);

  void show(@NotNull PsiElement element, @NotNull Editor editor, @NotNull Point point, boolean keyTriggered);

  void hide(@Nullable("if disposed") PsiElement element, @NotNull Editor editor);
}
=======
  /**
   * Provides an array of target declarations for given {@code sourceElement}.
   *
   *
   * @param sourceElement input psiElement
   * @param offset offset in the file
   *@param editor  @return all target declarations as an array of  {@code PsiElement} or null if none was found
   */
  @Nullable
  PsiElement[] getGotoDeclarationTargets(PsiElement sourceElement, int offset, Editor editor);

  /**
   * Provides the custom action text
   * @return the custom text or null to use the default text
   * @param context the action data context
   */
  @Nullable
  String getActionText(DataContext context);
}
>>>>>>> origin/115:platform/lang-impl/src/com/intellij/codeInsight/navigation/actions/GotoDeclarationHandler.java
