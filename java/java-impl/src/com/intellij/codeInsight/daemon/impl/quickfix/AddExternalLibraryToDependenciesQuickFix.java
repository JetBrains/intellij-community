/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
class AddExternalLibraryToDependenciesQuickFix extends OrderEntryFix {
  private final Module myCurrentModule;
  private final PsiReference myReference;
  private final ExternalLibraryDescriptor myLibraryDescriptor;
  private final String myQualifiedClassName;

  public AddExternalLibraryToDependenciesQuickFix(@NotNull Module currentModule,
                                                  @NotNull ExternalLibraryDescriptor libraryDescriptor, @NotNull PsiReference reference,
                                                  @Nullable String qualifiedClassName) {
    myCurrentModule = currentModule;
    myReference = reference;
    myLibraryDescriptor = libraryDescriptor;
    myQualifiedClassName = qualifiedClassName;
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
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    List<String> classesRoots = myLibraryDescriptor.locateLibraryClassesRoots(myCurrentModule);
    if (!classesRoots.isEmpty()) {
      String libraryName = classesRoots.size() > 1 ? myLibraryDescriptor.getPresentableName() : null;
      addJarsToRootsAndImportClass(classesRoots, libraryName, myCurrentModule, editor, myReference,
                                   myQualifiedClassName);
    }
  }
}
