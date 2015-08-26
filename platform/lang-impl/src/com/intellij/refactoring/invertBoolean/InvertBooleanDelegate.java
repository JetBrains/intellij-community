/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.invertBoolean;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class InvertBooleanDelegate {
  public static final ExtensionPointName<InvertBooleanDelegate> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.invertBoolean");

  /**
   * Quick check if element is potentially acceptable by delegate
   * 
   * @return true if element is possible to invert, e.g. variable or method
   */
  public abstract boolean isVisibleOnElement(@NotNull PsiElement element);

  /**
   * @return true if element is of boolean type
   */
  public abstract boolean isAvailableOnElement(@NotNull PsiElement element);

  /**
   * Adjust element to invert, e.g. suggest to refactor super method instead of current
   * 
   * @return null if user canceled the operation
   */
  @Nullable 
  public abstract PsiElement adjustElement(PsiElement element, Project project, Editor editor);

  /**
   * Eventually collect additional elements to rename, e.g. override methods
   * and find expressions which need to be inverted
   * 
   * @param renameProcessor null if element is not named or name was not changed
   */
  public abstract void collectRefElements(PsiElement element,
                                          Collection<PsiElement> elementsToInvert,
                                          @Nullable RenameProcessor renameProcessor,
                                          @NotNull String newName);

  /**
   * Replace expression with created negation
   * @param expression to be inverted, found in {@link #collectRefElements(PsiElement, Collection, RenameProcessor, String)}
   */
  public abstract void replaceWithNegatedExpression(PsiElement expression);

  /**
   * Initialize variable with negated default initializer when default was initially omitted
   */
  public void invertDefaultElementInitializer(PsiElement var) {}
  
  /**
   * Detect usages which can't be inverted
   */
  public void findConflicts(MultiMap<PsiElement, String> conflicts,
                            UsageInfo[] usageInfos) {}
}
