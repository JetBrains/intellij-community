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
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class ClassNameMacro implements Macro {

  public String getName() {
    return "className";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.classname");
  }

  public String getDefaultValue() {
    return "";
  }

  public Result calculateResult(@NotNull Expression[] params, final ExpressionContext context) {
    Project project = context.getProject();
    int templateStartOffset = context.getTemplateStartOffset();
    int offset = templateStartOffset > 0 ? context.getTemplateStartOffset() - 1 : context.getTemplateStartOffset();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement place = file.findElementAt(offset);
    PsiClass aClass = null;

    while(place != null){
      if (place instanceof PsiClass && !(place instanceof PsiAnonymousClass) && !(place instanceof PsiTypeParameter)){
        aClass = (PsiClass)place;
        // if className() is evaluated outside of the body of inner class, return name of its outer class instead (IDEADEV-19865)
        final PsiElement lBrace = aClass.getLBrace();
        if (lBrace != null && offset < lBrace.getTextOffset() && aClass.getContainingClass() != null) {
          aClass = aClass.getContainingClass();
        }
        break;
      }
      if (place instanceof PsiJavaFile){
        PsiClass[] classes = ((PsiJavaFile)place).getClasses();
        aClass = classes.length != 0 ? classes[0] : null;
        break;
      }
      place = place.getParent();
    }

    if (aClass == null) return null;
    String result = aClass.getName();
    while (aClass.getContainingClass() != null && aClass.getContainingClass().getName() != null) {
      result = aClass.getContainingClass().getName() + "$" + result;
      aClass = aClass.getContainingClass();
    }
    return new TextResult(result);
  }

  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    return null;
  }

  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, final ExpressionContext context) {
    return null;
  }
}
