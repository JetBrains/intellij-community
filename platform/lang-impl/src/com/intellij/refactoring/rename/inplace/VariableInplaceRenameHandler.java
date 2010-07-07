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

package com.intellij.refactoring.rename.inplace;

import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import org.jetbrains.annotations.NotNull;

public final class VariableInplaceRenameHandler implements RenameHandler {
  private static final ThreadLocal<Boolean> ourPreventInlineRenameFlag = new ThreadLocal<Boolean>();
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler");

  public boolean isAvailableOnDataContext(final DataContext dataContext) {
    final PsiElement element = PsiElementRenameHandler.getElement(dataContext);
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    final PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
    if (editor == null || file == null) return false;

    if (ourPreventInlineRenameFlag.get() != null) {
      return false;
    }
    final PsiElement nameSuggestionContext = file.findElementAt(editor.getCaretModel().getOffset());

    final RefactoringSupportProvider supportProvider = element != null ? LanguageRefactoringSupport.INSTANCE.forLanguage(element.getLanguage()):null;
    return supportProvider != null &&
           editor.getSettings().isVariableInplaceRenameEnabled() &&
           supportProvider.doInplaceRenameFor(element, nameSuggestionContext);
  }

  public boolean isRenaming(final DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    PsiElement element = PsiElementRenameHandler.getElement(dataContext);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    doRename(element, editor, dataContext);
  }

  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, final DataContext dataContext) {
    PsiElement element = elements.length == 1 ? elements[0] : null;
    if (element == null) element = PsiElementRenameHandler.getElement(dataContext);
    LOG.assertTrue(element != null);
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    doRename(element, editor, dataContext);
  }

  private void doRename(final PsiElement elementToRename, final Editor editor, final DataContext dataContext) {
    if (!isAvailableOnDataContext(dataContext)) {
      LOG.error("Recursive invocation");
      RenameHandlerRegistry.getInstance().getRenameHandler(dataContext).invoke(
        elementToRename.getProject(),
        editor,
        elementToRename.getContainingFile(), dataContext
      );
      return;
    }

    final boolean startedRename = new VariableInplaceRenamer((PsiNameIdentifierOwner)elementToRename, editor).performInplaceRename();

    if (!startedRename) {
      try {
        ourPreventInlineRenameFlag.set(Boolean.TRUE);

        RenameHandler handler = RenameHandlerRegistry.getInstance().getRenameHandler(dataContext);
        assert handler != null;
        handler.invoke(
          elementToRename.getProject(),
          editor,
          elementToRename.getContainingFile(), dataContext
        );
      } finally {
        ourPreventInlineRenameFlag.set(null);
      }
    }
  }
}
