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

package com.intellij.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.event.PomModelEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author mike
 *
 * @deprecated use {@link com.intellij.psi.impl.include.FileIncludeManager} instead
 */
public interface PsiIncludeManager {
  ExtensionPointName<PsiIncludeHandler> EP_NAME = new ExtensionPointName<PsiIncludeHandler>("com.intellij.psi.includeHandler");

  @NotNull
  PsiFile[] getIncludingFiles(@NotNull PsiFile file);

  void includeProcessed(@NotNull PsiElement includeDirective);

  interface PsiIncludeHandler {
    boolean shouldCheckFile(@NotNull VirtualFile psiFile);
    IncludeInfo[] findIncludes(PsiFile psiFile);

    void includeChanged(final PsiElement includeDirective, final PsiFile targetFile, final PomModelEvent event);
  }

  final class IncludeInfo {
    public static final IncludeInfo[] EMPTY_ARRAY = new IncludeInfo[0];
    public @Nullable final PsiFile targetFile;
    public @Nullable final PsiElement includeDirective;
    public @NotNull final String[] possibleTargetFileNames;
    public @NotNull final PsiIncludeHandler handler;

    public IncludeInfo(@Nullable final PsiFile targetFile,
                       @Nullable final PsiElement includeDirective,
                       String[] possibleTargetFileNames, final PsiIncludeHandler handler) {
      this.targetFile = targetFile;
      this.includeDirective = includeDirective;
      this.possibleTargetFileNames = possibleTargetFileNames;
      this.handler = handler;
    }
  }
}
