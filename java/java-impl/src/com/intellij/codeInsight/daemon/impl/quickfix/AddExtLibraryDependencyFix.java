/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
class AddExtLibraryDependencyFix extends OrderEntryFix {
  private final Module myCurrentModule;
  private final ExternalLibraryDescriptor myLibraryDescriptor;
  private final DependencyScope myScope;
  private final String myQualifiedClassName;

  public AddExtLibraryDependencyFix(PsiReference reference,
                                    Module currentModule,
                                    ExternalLibraryDescriptor descriptor,
                                    DependencyScope scope,
                                    String qName) {
    super(reference);
    myCurrentModule = currentModule;
    myLibraryDescriptor = descriptor;
    myScope = scope;
    myQualifiedClassName = qName;
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return "Add '" + myLibraryDescriptor.getPresentableName() + "' to classpath";
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return !project.isDisposed() && !myCurrentModule.isDisposed();
  }

  @Override
  public void invoke(@NotNull Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    JavaProjectModelModificationService.getInstance(project)
      .addDependency(myCurrentModule, myLibraryDescriptor, myScope)
      .done(aVoid -> new WriteAction() {
        protected void run(@NotNull final Result result) {
          try {
            importClass(myCurrentModule, editor, restoreReference(), myQualifiedClassName);
          }
          catch (IndexNotReadyException e) {
            Logger.getInstance(AddExtLibraryDependencyFix.class).info(e);
          }
        }
      }.execute());
  }

}