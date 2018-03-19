// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

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
                                       .onSuccess(aVoid -> WriteAction.runAndWait(() -> {
                                         try {
                                           importClass(myCurrentModule, editor, restoreReference(), myQualifiedClassName);
                                         }
                                         catch (IndexNotReadyException e) {
                                           Logger.getInstance(AddExtLibraryDependencyFix.class).info(e);
                                         }
                                       }));
  }

}