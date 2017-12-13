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
package com.intellij.packageDependencies.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiQualifiedNamedElement;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerComposite;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import org.jetbrains.annotations.NotNull;

public class RefactoringScopeElementListenerProvider implements RefactoringElementListenerProvider {
  private static final Logger LOG = Logger.getInstance(RefactoringScopeElementListenerProvider.class);

  private enum ReferenceKind {QUALIFIED_NAME, FILE_PATH}

  @Override
  public RefactoringElementListener getListener(PsiElement element) {
    if (!(element instanceof PsiQualifiedNamedElement) && !(element instanceof PsiDirectory)) return null;

    final PsiFile containingFile = element.getContainingFile();

    RefactoringElementListenerComposite composite = new RefactoringElementListenerComposite();
    registerListeners(element, composite, containingFile, ReferenceKind.FILE_PATH);
    registerListeners(element, composite, containingFile, ReferenceKind.QUALIFIED_NAME);

    return composite;
  }

  private static void registerListeners(PsiElement element,
                                        RefactoringElementListenerComposite result,
                                        PsiFile containingFile,
                                        ReferenceKind referenceKind) {
    String oldName = getQualifiedName(element, referenceKind);
    for (final NamedScopesHolder holder : NamedScopesHolder.getAllNamedScopeHolders(element.getProject())) {
      final NamedScope[] scopes = holder.getEditableScopes();
      for (int i = 0; i < scopes.length; i++) {
        final NamedScope scope = scopes[i];
        final PackageSet packageSet = scope.getValue();
        if (packageSet != null && (containingFile == null || packageSet.contains(containingFile, holder))) {
          registerListeners(packageSet, result, new OldScopeDescriptor(scope, i, holder), oldName, referenceKind);
        }
      }
    }
  }

  private static String getQualifiedName(PsiElement element, ReferenceKind referenceKind) {
    if (referenceKind == ReferenceKind.QUALIFIED_NAME) {
      return element instanceof PsiQualifiedNamedElement ? ((PsiQualifiedNamedElement)element).getQualifiedName() : null;
    }
    else {
      final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
      if (virtualFile == null) {
        return null;
      }
      final Project project = element.getProject();
      return FilePatternPackageSet.getRelativePath(virtualFile, ProjectRootManager.getInstance(project).getFileIndex(), true, project.getBaseDir());
    }
  }

  private static PackageSet updateNameInPattern(PackageSet packageSet, String oldName, String newName) {
    if ((packageSet instanceof PatternBasedPackageSet) && ((PatternBasedPackageSet)packageSet).isOn(oldName)) {
      return ((PatternBasedPackageSet)packageSet).updatePattern(oldName, newName);
    }
    return packageSet;
  }

  private static void registerListeners(PackageSet packageSet,
                                        RefactoringElementListenerComposite result,
                                        OldScopeDescriptor descriptor,
                                        String oldQualifiedName, ReferenceKind referenceKind) {
    PackageSet oldSet = descriptor.getOldScope().getValue();
    if (oldSet != null && packageSet.anyMatches(set -> set instanceof PatternBasedPackageSet && ((PatternBasedPackageSet)set).isOn(oldQualifiedName))) {
      result.addListener(new RefactoringElementAdapter() {
        @Override
        public void elementRenamedOrMoved(@NotNull PsiElement newElement) {
          LOG.assertTrue(newElement instanceof PsiQualifiedNamedElement || newElement instanceof PsiDirectory);
          String newName = getQualifiedName(newElement, referenceKind);
          if (newName != null) {
            PackageSet newSet = oldSet.map(set -> updateNameInPattern(set, oldQualifiedName, newName));
            if (newSet != oldSet) {
              descriptor.replaceScope(new NamedScope(descriptor.getOldScope().getName(), newSet));
            }
          }
        }

        @Override
        public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
          LOG.assertTrue(newElement instanceof PsiQualifiedNamedElement || newElement instanceof PsiDirectory);
          descriptor.replaceScope(descriptor.getOldScope());
        }
      });
    }
  }

  private static class OldScopeDescriptor {
    private final NamedScopesHolder myHolder;
    private final int myIndex;
    private final NamedScope myOldScope;

    private OldScopeDescriptor(NamedScope oldScope, int index, NamedScopesHolder holder) {
      myHolder = holder;
      myIndex = index;
      myOldScope = oldScope;
    }

    public NamedScope getOldScope() {
      return myOldScope;
    }

    public void replaceScope(NamedScope newScope) {
      NamedScope[] currentScopes = myHolder.getEditableScopes();
      if (myIndex < currentScopes.length) {
        currentScopes[myIndex] = newScope;
        myHolder.setScopes(currentScopes);
      }
    }
  }
}
