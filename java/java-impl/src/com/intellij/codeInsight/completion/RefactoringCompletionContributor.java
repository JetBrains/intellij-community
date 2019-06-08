// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.ui.ClassNameReferenceEditor;
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

    resultSet.runRemainingContributors(parameters, result -> {
      LookupElement element = result.getLookupElement();
      Object object = element.getObject();
      if (object instanceof PsiClass) {
        Module module = ModuleUtilCore.findModuleForPsiElement((PsiClass)object);
        if (module != null) {
          resultSet.consume(LookupElementDecorator.withRenderer(element, new AppendModuleName(module)));
          return;
        }
      }
      resultSet.passResult(result);
    });
  }

  private static class AppendModuleName extends LookupElementRenderer<LookupElementDecorator<LookupElement>> {
    private final Module myModule;

    AppendModuleName(Module module) {
      myModule = module;
    }

    @Override
    public void renderElement(LookupElementDecorator<LookupElement> element, LookupElementPresentation presentation) {
      element.getDelegate().renderElement(presentation);
      presentation.appendTailText(" [" + myModule.getName() + "]", true);
    }
  }
}
