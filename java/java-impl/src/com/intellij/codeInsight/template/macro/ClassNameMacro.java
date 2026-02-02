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

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiTypeParameter;
import org.jetbrains.annotations.NotNull;

public final class ClassNameMacro extends Macro {

  @Override
  public String getName() {
    return "className";
  }

  @Override
  public Result calculateResult(Expression @NotNull [] params, final ExpressionContext context) {
    int templateStartOffset = context.getTemplateStartOffset();
    int offset = templateStartOffset > 0 ? context.getTemplateStartOffset() - 1 : context.getTemplateStartOffset();
    boolean skipCheckInFile = params.length > 0 && params[0].calculateResult(context).toString().equals("true");
    PsiElement place = context.getPsiElementAtStartOffset();
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
      if (place instanceof PsiFile && skipCheckInFile){
        return null;
      }
      if (place instanceof PsiJavaFile){
        PsiClass[] classes = ((PsiJavaFile)place).getClasses();
        aClass = classes.length != 0 ? classes[0] : null;
        break;
      }
      place = place.getParent();
    }

    if (aClass == null) return null;
    if (aClass instanceof PsiImplicitClass && skipCheckInFile) return null;
    String qname = aClass.getName();
    return qname == null ? null : new TextResult(qname);
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }


}
