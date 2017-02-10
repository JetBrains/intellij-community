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

package com.intellij.psi.impl.source.jsp;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.jsp.BaseJspFile;
import com.intellij.psi.jsp.JspFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class JspContextManager {

  public static JspContextManager getInstance(Project project) {
    return project.getComponent(JspContextManager.class);
  }

  public abstract BaseJspFile[] getSuitableContextFiles(@NotNull PsiFile file);

  public abstract void setContextFile(@NotNull PsiFile file, @Nullable BaseJspFile contextFile);

  @Nullable
  public abstract
  BaseJspFile getContextFile(@NotNull PsiFile file);

  @Nullable
  public abstract JspFile getConfiguredContextFile(@NotNull PsiFile file);

  @NotNull
  public
  BaseJspFile getRootContextFile(@NotNull BaseJspFile file) {
    BaseJspFile rootContext = file;
    HashSet<BaseJspFile> recursionPreventer = new HashSet<>();
    do {
      recursionPreventer.add(rootContext);
      BaseJspFile context = getContextFile(rootContext);
      if (context == null || recursionPreventer.contains(context)) break;
      rootContext = context;
    }
    while (true);

    return rootContext;
  }
}
