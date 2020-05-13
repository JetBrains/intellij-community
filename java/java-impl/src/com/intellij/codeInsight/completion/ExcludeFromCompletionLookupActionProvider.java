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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupActionProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class ExcludeFromCompletionLookupActionProvider implements LookupActionProvider {
  @Override
  public void fillActions(LookupElement element, Lookup lookup, Consumer<LookupElementAction> consumer) {
    Object o = element.getObject();
    if (o instanceof PsiClassObjectAccessExpression) {
      o = PsiUtil.resolveClassInType(((PsiClassObjectAccessExpression)o).getOperand().getType());
    }
    
    if (o instanceof PsiClass) {
      PsiClass clazz = (PsiClass)o;
      addExcludes(consumer, clazz, clazz.getQualifiedName());
    } else if (o instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)o;
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        addExcludes(consumer, method, PsiUtil.getMemberQualifiedName(method));
      }
    } else if (o instanceof PsiField) {
      final PsiField field = (PsiField)o;
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        addExcludes(consumer, field, PsiUtil.getMemberQualifiedName(field));
      }
    }
  }

  private static void addExcludes(Consumer<? super LookupElementAction> consumer, PsiMember element, @Nullable String qname) {
    if (qname == null) return;
    final Project project = element.getProject();
    for (final String s : AddImportAction.getAllExcludableStrings(qname)) {
      consumer.consume(new ExcludeFromCompletionAction(project, s));
    }
  }

  private static class ExcludeFromCompletionAction extends LookupElementAction {
    private final Project myProject;
    private final String myToExclude;

    ExcludeFromCompletionAction(@NotNull Project project, @NotNull String s) {
      super(null, JavaBundle.message("exclude.0.from.completion", s));
      myProject = project;
      myToExclude = s;
    }

    @Override
    public Result performLookupAction() {
      AddImportAction.excludeFromImport(myProject, myToExclude);
      return Result.HIDE_LOOKUP;
    }
  }
}
