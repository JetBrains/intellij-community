/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public abstract class IndentHelper {
  public static IndentHelper getInstance() {
    return ServiceManager.getService(IndentHelper.class);
  }

  /**
   * @deprecated Use {@link #getIndent(PsiFile, ASTNode)}
   */
  @SuppressWarnings("unused")
  @Deprecated
  public final int getIndent(Project project, FileType fileType, ASTNode element) {
    return getIndent(getFile(element), element);
  }

  /**
   * @deprecated Use {@link #getIndent(PsiFile, ASTNode, boolean)}
   */
  @SuppressWarnings("unused")
  @Deprecated
  public final int getIndent(Project project, FileType fileType, ASTNode element, boolean includeNonSpace) {
    return getIndent(getFile(element), element, includeNonSpace);
  }

  private static PsiFile getFile(ASTNode element) {
    return element.getPsi().getContainingFile();
  }

  public abstract int getIndent(@NotNull PsiFile file, @NotNull ASTNode element);

  public abstract int getIndent(@NotNull PsiFile file, @NotNull ASTNode element, boolean includeNonSpace);
}
