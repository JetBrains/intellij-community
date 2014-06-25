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

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.ui.ClassNameReferenceEditor;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class RefactoringCompletionContributor extends CompletionContributor {
  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull final CompletionResultSet resultSet) {
    if (parameters.getOriginalFile().getUserData(ClassNameReferenceEditor.CLASS_NAME_REFERENCE_FRAGMENT) == null) {
      return;
    }
    
    resultSet.runRemainingContributors(parameters, new Consumer<CompletionResult>() {
      @Override
      public void consume(CompletionResult result) {
        LookupElement element = result.getLookupElement();
        Object object = element.getObject();
        if (object instanceof PsiClass) {
          Module module = ModuleUtil.findModuleForPsiElement((PsiClass)object);
          if (module != null) {
            resultSet.consume(LookupElementDecorator.withRenderer(element, new AppendModuleName(module)));
            return;
          }
        }
        resultSet.passResult(result);
      }
    });
  }

  private static class AppendModuleName extends LookupElementRenderer<LookupElementDecorator<LookupElement>> {
    private final Module myModule;

    public AppendModuleName(Module module) {
      myModule = module;
    }

    @Override
    public void renderElement(LookupElementDecorator<LookupElement> element, LookupElementPresentation presentation) {
      element.getDelegate().renderElement(presentation);
      presentation.appendTailText(" [" + myModule.getName() + "]", true);
    }
  }
}
