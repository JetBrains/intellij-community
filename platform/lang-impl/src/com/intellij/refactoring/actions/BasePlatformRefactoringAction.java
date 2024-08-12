// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.lang.ElementsHandler;
import com.intellij.util.CachedValueImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class BasePlatformRefactoringAction extends BaseRefactoringAction {
  private final CachedValue<Boolean> myHidden = new CachedValueImpl<>(
    () -> CachedValueProvider.Result.create(calcHidden(), LanguageRefactoringSupport.INSTANCE));
  private final Condition<RefactoringSupportProvider> myCondition = provider -> getRefactoringHandler(provider) != null;

  @Override
  protected final RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    PsiElement element = null;
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (editor != null && file != null) {
      element = getElementAtCaret(editor, file);
      if (element != null) {
        RefactoringActionHandler handler = getHandler(element.getLanguage(), element);
        if (handler != null) {
          return handler;
        }
      }
    }

    PsiElement referenced = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (referenced != null) {
      RefactoringActionHandler handler = getHandler(referenced.getLanguage(), referenced);
      if (handler != null) {
        return handler;
      }
    }

    PsiElement[] psiElements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    if (psiElements != null && psiElements.length > 1) {
      RefactoringActionHandler handler = getHandler(psiElements[0].getLanguage(), psiElements[0]);
      if (handler != null && isEnabledOnElements(psiElements)) {
        return handler;
      }
    }

    if (element == null) {
      element = referenced;
    }

    final Language[] languages = LangDataKeys.CONTEXT_LANGUAGES.getData(dataContext);
    if (languages != null) {
      for (Language language : languages) {
        RefactoringActionHandler handler = getHandler(language, element);
        if (handler != null) {
          return handler;
        }
      }
    }

    return null;
  }

  protected @Nullable RefactoringActionHandler getHandler(@NotNull Language language, PsiElement element) {
    List<RefactoringSupportProvider> providers = LanguageRefactoringSupport.INSTANCE.allForLanguage(language);
    if (providers.isEmpty()) return null;
    if (element == null) return getRefactoringHandler(providers.get(0));
    for (RefactoringSupportProvider provider : providers) {
      if (provider.isAvailable(element)) {
        return getRefactoringHandler(provider, element);
      }
    }
    return null;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element, @NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    return getHandler(context) != null;
  }

  @Override
  protected boolean isAvailableForLanguage(final Language language) {
    List<RefactoringSupportProvider> providers = LanguageRefactoringSupport.INSTANCE.allForLanguage(language);
    return ContainerUtil.find(providers, myCondition) != null;
  }

  @Override
  protected boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    if (elements.length > 0) {
      Language language = elements[0].getLanguage();
      RefactoringActionHandler handler = getHandler(language, elements[0]);
      return handler instanceof ElementsHandler && ((ElementsHandler)handler).isEnabledOnElements(elements);
    }
    return false;
  }

  protected abstract @Nullable RefactoringActionHandler getRefactoringHandler(@NotNull RefactoringSupportProvider provider);

  protected @Nullable RefactoringActionHandler getRefactoringHandler(@NotNull RefactoringSupportProvider provider, PsiElement element) {
    return getRefactoringHandler(provider);
  }

  @Override
  protected boolean isHidden() {
    return myHidden.getValue().booleanValue();
  }

  private boolean calcHidden() {
    List<Language> languages = new ArrayList<>(Language.getRegisteredLanguages());
    languages.sort((o1, o2) -> {
      // Java supports most of refactorings,
      // so it is faster to just check it first without loading all language extensions
      if ("JAVA".equals(o1.getID())) return -1;
      if ("JAVA".equals(o2.getID())) return 1;

      return o1.getID().compareTo(o2.getID());
    });

    for (Language l: languages) {
      if (isAvailableForLanguage(l)) {
        return false;
      }
    }
    return true;
  }
}
