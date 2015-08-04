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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
* @author peter
*/
public class JavaStaticMemberProcessor extends StaticMemberProcessor {
  private final PsiElement myOriginalPosition;

  public JavaStaticMemberProcessor(CompletionParameters parameters) {
    super(parameters.getPosition());
    myOriginalPosition = parameters.getOriginalPosition();

    final PsiFile file = parameters.getPosition().getContainingFile();
    if (file instanceof PsiJavaFile) {
      final PsiImportList importList = ((PsiJavaFile)file).getImportList();
      if (importList != null) {
        for (PsiImportStaticStatement statement : importList.getImportStaticStatements()) {
          importMembersOf(statement.resolveTargetClass());
        }
      }
    }
  }

  @NotNull
  @Override
  protected LookupElement createLookupElement(@NotNull PsiMember member, @NotNull final PsiClass containingClass, boolean shouldImport) {
    shouldImport |= myOriginalPosition != null && PsiTreeUtil.isAncestor(containingClass, myOriginalPosition, false);

    if (member instanceof PsiMethod) {
      return AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(new GlobalMethodCallElement((PsiMethod)member, shouldImport, false));
    }
    return AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(new VariableLookupItem((PsiField)member, shouldImport) {
      @Override
      public void handleInsert(InsertionContext context) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME);

        super.handleInsert(context);
      }
    });
  }

  @Override
  protected LookupElement createLookupElement(@NotNull List<PsiMethod> overloads,
                                              @NotNull PsiClass containingClass,
                                              boolean shouldImport) {
    shouldImport |= myOriginalPosition != null && PsiTreeUtil.isAncestor(containingClass, myOriginalPosition, false);

    final JavaMethodCallElement element = new GlobalMethodCallElement(overloads.get(0), shouldImport, true);
    JavaCompletionUtil.putAllMethods(element, overloads);
    return element;
  }

  private static class GlobalMethodCallElement extends JavaMethodCallElement {
    public GlobalMethodCallElement(PsiMethod member, boolean shouldImport, boolean mergedOverloads) {
      super(member, shouldImport, mergedOverloads);
    }

    @Override
    public void handleInsert(InsertionContext context) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME);

      super.handleInsert(context);
    }
  }
}
