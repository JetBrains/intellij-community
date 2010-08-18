/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public abstract class BasePlatformRefactoringAction extends BaseRefactoringAction {
  private Boolean myHidden = null;

  public BasePlatformRefactoringAction() {
    LanguageRefactoringSupport.INSTANCE.addListener(new ExtensionPointListener<RefactoringSupportProvider>() {
      public void extensionAdded(RefactoringSupportProvider extension, @Nullable PluginDescriptor pluginDescriptor) {
        myHidden = null;
      }

      public void extensionRemoved(RefactoringSupportProvider extension, @Nullable PluginDescriptor pluginDescriptor) {
        myHidden = null;
      }
    });
  }

  @Override
  protected final RefactoringActionHandler getHandler(DataContext dataContext) {
    final Language[] languages = LangDataKeys.CONTEXT_LANGUAGES.getData(dataContext);
    if (languages != null) {
      for (Language language : languages) {
        List<RefactoringSupportProvider> providers = LanguageRefactoringSupport.INSTANCE.allForLanguage(language);
        if (providers.isEmpty()) continue;
        if (providers.size() == 1) return getRefactoringHandler(providers.get(0));
        PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
        if (element != null) {
          for (RefactoringSupportProvider provider : providers) {
            if (provider.isAvailable(element)) {
              return getRefactoringHandler(provider);
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  protected abstract RefactoringActionHandler getRefactoringHandler(@NotNull RefactoringSupportProvider provider);

  @Override
  protected boolean isHidden() {
    if (myHidden == null) {
      myHidden = calcHidden();
    }
    return myHidden.booleanValue();
  }

  private boolean calcHidden() {
    for(Language l: Language.getRegisteredLanguages()) {
      if (isAvailableForLanguage(l)) {
        return false;
      }
    }
    return true;
  }
}
