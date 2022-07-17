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
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Provides helpers to handle psi classes containing in the same psi file.
 * Depending on implementation e.g. for Java or Drools different types of psi classes are considered.
 */
public abstract class MoveAllClassesInFileHandler {
  public static final ExtensionPointName<MoveAllClassesInFileHandler> EP_NAME =
    new ExtensionPointName<>("com.intellij.refactoring.moveAllClassesInFileHandler");

  /**
   * Helps to find psi classes containing in the same psi file with <code>psiClass</code> and put them as keys
   * into <code>allClasses</code>.
   * If all the found classes are going to be moved i.e. all of them contain in <code>elementsToMove</code>,
   * then <code>true</code> value will be set for each entry in <code>allClasses</code>.
   * If at least one is not going to be moved, code>false</code> value will be set then.
   *
   * <p> The method should be called under read action.
   */
  public abstract void processMoveAllClassesInFile(@NotNull Map<PsiClass, Boolean> allClasses, @NotNull PsiClass psiClass,
                                                   PsiElement... elementsToMove);
}

