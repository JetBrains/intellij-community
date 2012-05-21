/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nullable;
import com.intellij.psi.jsp.JspFile;

/**
 * @author yole
 */
public class JspPsiUtil {
  public static boolean isInJspFile(@Nullable final PsiElement element) {
    return getJspFile(element) != null;
  }

  @Nullable
  public static JspFile getJspFile(final PsiElement element) {
    final PsiFile psiFile = PsiUtilCore.getTemplateLanguageFile(element);
    return psiFile instanceof JspFile ? (JspFile)psiFile : null;

    /*final FileViewProvider provider = element.getContainingFile().getViewProvider();
    PsiFile file = provider.getPsi(StdLanguages.JSP);
    if (file instanceof JspFile) return (JspFile)file;
    file = provider.getPsi(StdLanguages.JSPX);
    return file instanceof JspFile ? (JspFile)file : null;*/
  }
}
