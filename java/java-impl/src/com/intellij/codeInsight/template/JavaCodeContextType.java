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
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.DataManager;
import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

public class JavaCodeContextType extends TemplateContextType {
  public JavaCodeContextType() {
    super("JAVA_CODE", CodeInsightBundle.message("dialog.edit.template.checkbox.java.code"));
  }


  public boolean isInContext(@NotNull final PsiFile file, final int offset) {
    if (PsiUtilBase.getLanguageAtOffset(file, offset).isKindOf(StdLanguages.JAVA)) {
      PsiElement element = file.findElementAt(offset);
      if (element instanceof PsiWhiteSpace && offset > 0) {
        element = file.findElementAt(offset - 1);
      }
      if (element != null &&
          element.getParent() instanceof PsiReferenceExpression &&
          ((PsiReferenceExpression)element.getParent()).isQualified()) {
        return false;
      }

      return element != null && PsiTreeUtil.getParentOfType(element, PsiComment.class, false) == null &&
             !(element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.STRING_LITERAL);
    }

    return false;
  }

  @Override
  public boolean isInContext(@NotNull final FileType fileType) {
    return fileType == StdFileTypes.JAVA || fileType == StdFileTypes.JSP || fileType == StdFileTypes.JSPX;
  }

  @NotNull
  @Override
  public SyntaxHighlighter createHighlighter() {
    return new JavaFileHighlighter();
  }

  @Override
  public Document createDocument(CharSequence text, Project project) {
    if (project == null) {
      return super.createDocument(text, project);
    }
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = psiFacade.getElementFactory();
    final JavaCodeFragment fragment = factory.createCodeBlockCodeFragment((String)text, psiFacade.findPackage(""), true);
    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }
}
