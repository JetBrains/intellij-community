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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class JavaClassTooltipLinkHandler extends TooltipLinkHandler {

  @Override
  public boolean handleLink(@NotNull String refSuffix, @NotNull Editor editor) {
    Project project = editor.getProject();
    if (project == null) return false;
    PsiElement aClass = JavaPsiFacade.getInstance(project).findClass(refSuffix, GlobalSearchScope.allScope(project));
    if (aClass == null) return false;
    NavigationUtil.activateFileWithPsiElement(aClass);
    return true;
  }
}
