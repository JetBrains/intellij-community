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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.PsiTypeResult;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.impl.JavaTemplateUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class TypeExpression extends Expression {
  private final LinkedHashSet<SmartTypePointer> myItems;

  public TypeExpression(final Project project, PsiType[] types) {
    final SmartTypePointerManager manager = SmartTypePointerManager.getInstance(project);
    myItems = new LinkedHashSet<>();
    for (final PsiType type : types) {
      myItems.add(manager.createSmartTypePointer(type));
    }
  }

  @Override
  public Result calculateResult(ExpressionContext context) {
    final Project project = context.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    if (myItems.isEmpty()) return null;

    final PsiType type = myItems.iterator().next().getType();
    return type == null? null : new PsiTypeResult(type, project) {
      @Override
      public void handleRecalc(PsiFile psiFile, Document document, int segmentStart, int segmentEnd) {
        if (myItems.size() <= 1) {
          super.handleRecalc(psiFile, document, segmentStart, segmentEnd);
        } else {
          JavaTemplateUtil.updateTypeBindings(getType(), psiFile, document, segmentStart, segmentEnd, true);
        }
      }

      @Override
      public String toString() {
        final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(project);
        return myItems.size() == 1 || settings.isUseFqClassNames() ? super.toString() : type.getPresentableText();
      }
    };
  }

  @Override
  public Result calculateQuickResult(ExpressionContext context) {
    return calculateResult(context);
  }

  @Override
  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    if (myItems.size() <= 1) return null;
    PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
    
    List<LookupElement> result = new ArrayList<>(myItems.size());
    for (final SmartTypePointer item : myItems) {
      final PsiType type = item.getType();
      if (type != null) {
        result.add(PsiTypeLookupItem.createLookupItem(type, null));
      }
    }
    return result.toArray(new LookupElement[result.size()]);
  }

}
