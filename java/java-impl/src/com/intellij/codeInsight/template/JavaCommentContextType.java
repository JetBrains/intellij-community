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
package com.intellij.codeInsight.template;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class JavaCommentContextType extends TemplateContextType {
  public JavaCommentContextType() {
    super("JAVA_COMMENT", JavaPsiBundle.message("context.type.java.comment"), JavaCodeContextType.Generic.class);
  }

  @Override
  public boolean isInContext(@NotNull final PsiFile file, final int offset) {
    if (PsiUtilCore.getLanguageAtOffset(file, offset).isKindOf(JavaLanguage.INSTANCE)) {
      PsiElement element = file.findElementAt(offset);
      if (element instanceof PsiWhiteSpace && offset > 0) {
        element = file.findElementAt(offset-1);
      }
      return PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null;
    }
    return false;
  }
}
