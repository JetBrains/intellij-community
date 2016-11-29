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

package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author dsl
 */
public abstract class SafeDeleteDialog extends DialogWrapper {
  private final Project myProject;
  private final Callback myCallback;
  private final boolean myIsDelete;

  protected final PsiElement[] myElements;
  protected final SafeDeleteProcessorDelegate myDelegate;

  public interface Callback {
    void run(SafeDeleteDialog dialog);
  }

  public SafeDeleteDialog(Project project, PsiElement[] elements, Callback callback, boolean isDelete) {
    super(project, true);
    myProject = project;
    myElements = elements;
    myCallback = callback;
    myDelegate = getDelegate();
    myIsDelete = isDelete;
    setTitle(SafeDeleteHandler.REFACTORING_NAME);
  }

  public abstract boolean isSearchInComments();

  public abstract boolean isSearchForTextOccurences();

  protected boolean isDelete() {
    return myIsDelete;
  }

  @Nullable
  private SafeDeleteProcessorDelegate getDelegate() {
    if (myElements.length == 1) {
      for (SafeDeleteProcessorDelegate delegate : Extensions.getExtensions(SafeDeleteProcessorDelegate.EP_NAME)) {
        if (delegate.handlesElement(myElements[0])) {
          return delegate;
        }
      }
    }
    return null;
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  protected boolean needSearchForTextOccurrences() {
    for (PsiElement element : myElements) {
      if (TextOccurrencesUtil.isSearchTextOccurencesEnabled(element)) {
        return true;
      }
    }
    return false;
  }


  @Override
  protected void doOKAction() {
    if (DumbService.isDumb(myProject)) {
      Messages.showMessageDialog(myProject, "Safe delete refactoring is not available while indexing is in progress", "Indexing", null);
      return;
    }

    NonProjectFileWritingAccessProvider.disableChecksDuring(() ->{
        if (myCallback != null && isSafeDelete()) {
          myCallback.run(this);
        } else {
          super.doOKAction();

      }
    });

    final RefactoringSettings refactoringSettings = RefactoringSettings.getInstance();
    if (isDelete()) {
      refactoringSettings.SAFE_DELETE_WHEN_DELETE = isSafeDeleteSelected();
    }
    if (isSafeDelete()) {
      if (myDelegate == null) {
        refactoringSettings.SAFE_DELETE_SEARCH_IN_COMMENTS = isSearchInComments();
        if (needSearchForTextOccurrences()) {
          refactoringSettings.SAFE_DELETE_SEARCH_IN_NON_JAVA = isSearchForTextOccurences();
        }
      }
      else {
        myDelegate.setToSearchInComments(myElements[0], isSearchInComments());

        if (needSearchForTextOccurrences()) {
          myDelegate.setToSearchForTextOccurrences(myElements[0], isSearchForTextOccurences());
        }
      }
    }
  }

  protected abstract boolean isSafeDeleteSelected();


  protected boolean isSafeDelete() {
    return !isDelete() || isSafeDeleteSelected();
  }
}
