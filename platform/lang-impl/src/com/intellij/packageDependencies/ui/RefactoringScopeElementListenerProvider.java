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
import org.jetbrains.annotations.Nullable;

public class RefactoringScopeElementListenerProvider implements RefactoringElementListenerProvider {
  private static final Logger LOG = Logger.getInstance(RefactoringScopeElementListenerProvider.class);

  @Override
  public RefactoringElementListener getListener(PsiElement element) {
    if (!(element instanceof PsiQualifiedNamedElement) && !(element instanceof PsiDirectory)) return null;

    final PsiFile containingFile = element.getContainingFile();

    RefactoringElementListenerComposite composite = new RefactoringElementListenerComposite();
    String oldName = getRelativePathToFile(element);
    if (oldName != null) {
      registerListeners(element, composite, containingFile, oldName);
    }

    if (element instanceof PsiQualifiedNamedElement) {
      oldName = ((PsiQualifiedNamedElement)element).getQualifiedName();
      if (oldName != null) {
        registerListeners(element, composite, containingFile, oldName);
      }
    }

    return composite;
  }

  private static void registerListeners(PsiElement element, RefactoringElementListenerComposite result, PsiFile containingFile,
                                        String oldName) {
    for (final NamedScopesHolder holder : NamedScopesHolder.getAllNamedScopeHolders(element.getProject())) {
      final NamedScope[] scopes = holder.getEditableScopes();
      for (int i = 0; i < scopes.length; i++) {
        final NamedScope scope = scopes[i];
        final PackageSet packageSet = scope.getValue();
        if (packageSet != null && (containingFile == null || packageSet.contains(containingFile, holder))) {
          registerListeners(packageSet, result, new OldScopeDescriptor(oldName, scope, i, holder));
        }
      }
    }
  }

  private static String getQualifiedName(PsiElement element, boolean acceptQNames) {
    if (element instanceof PsiQualifiedNamedElement && acceptQNames) {
      return ((PsiQualifiedNamedElement)element).getQualifiedName();
    }
    else {
      return getRelativePathToFile(element);
    }
  }

  @Nullable
  private static String getRelativePathToFile(PsiElement element) {
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
    if (virtualFile == null) {
      return null;
    }
    final Project project = element.getProject();
    return FilePatternPackageSet.getRelativePath(virtualFile, ProjectRootManager.getInstance(project).getFileIndex(), true,
                                                 project.getBaseDir());
  }

  private static void registerListeners(PackageSet packageSet,
                                        RefactoringElementListenerComposite result,
                                        OldScopeDescriptor scopeDescriptor) {
    if (packageSet instanceof PatternBasedPackageSet) {
      final PackageSet value = scopeDescriptor.getScope().getValue();
      if (value != null) {
        registerListenersPatternPackageSet(((PatternBasedPackageSet)packageSet), result, scopeDescriptor, value.getText());
      }
    }
    else if (packageSet instanceof ComplementPackageSet) {
      registerListeners(((ComplementPackageSet)packageSet).getComplementarySet(), result, scopeDescriptor);
    }
    else if (packageSet instanceof UnionPackageSet) {
      registerListeners(((UnionPackageSet)packageSet).getFirstSet(), result, scopeDescriptor);
      registerListeners(((UnionPackageSet)packageSet).getSecondSet(), result, scopeDescriptor);
    }
    else if (packageSet instanceof IntersectionPackageSet) {
      registerListeners(((IntersectionPackageSet)packageSet).getFirstSet(), result, scopeDescriptor);
      registerListeners(((IntersectionPackageSet)packageSet).getSecondSet(), result, scopeDescriptor);
    }
  }

  private static void registerListenersPatternPackageSet(PatternBasedPackageSet pattern, RefactoringElementListenerComposite composite,
                                                         OldScopeDescriptor descriptor,
                                                         String text) {
    if (pattern.isOn(descriptor.getOldQName())) {
      composite.addListener(new RefactoringElementAdapter() {
        @Override
        public void elementRenamedOrMoved(@NotNull PsiElement newElement) {
          LOG.assertTrue(newElement instanceof PsiQualifiedNamedElement || newElement instanceof PsiDirectory);
          try {
            final NamedScope[] currentScopes = descriptor.getHolder().getEditableScopes();
            final PackageSet currentPackageSet = currentScopes[descriptor.getIdx()].getValue();
            final String qualifiedName = getQualifiedName(newElement, !(currentPackageSet instanceof FilePatternPackageSet));
            if (qualifiedName != null) {
              final String newPattern = text.replace(descriptor.getOldQName(), qualifiedName);
              final PackageSet newSet = PackageSetFactory.getInstance().compile(newPattern);
              NamedScope newScope = new NamedScope(descriptor.getScope().getName(), newSet);
              currentScopes[descriptor.getIdx()] = newScope;
              descriptor.getHolder().setScopes(currentScopes);
            }
          }
          catch (ParsingException ignore) {
          }
        }

        @Override
        public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
          LOG.assertTrue(newElement instanceof PsiQualifiedNamedElement || newElement instanceof PsiDirectory);
          try {
            final NamedScope[] currentScopes = descriptor.getHolder().getEditableScopes();
            final PackageSet packageSet = currentScopes[descriptor.getIdx()].getValue();
            if (packageSet == null) return;
            NamedScope newScope = new NamedScope(descriptor.getScope().getName(), PackageSetFactory.getInstance().compile(text));
            currentScopes[descriptor.getIdx()] = newScope;
            descriptor.getHolder().setScopes(currentScopes);
          }
          catch (ParsingException ignore) {
          }
        }
      });
    }
  }

  private static class OldScopeDescriptor {
    private final String myOldQName;
    private final NamedScopesHolder myHolder;
    private final int myIdx;
    private final NamedScope myScope;

    private OldScopeDescriptor(final String oldQName,
                               final NamedScope scope,
                               final int idx,
                               final NamedScopesHolder holder) {
      myOldQName = oldQName;
      myHolder = holder;
      myIdx = idx;
      myScope = scope;
    }

    public String getOldQName() {
      return myOldQName;
    }

    public NamedScopesHolder getHolder() {
      return myHolder;
    }

    public int getIdx() {
      return myIdx;
    }

    public NamedScope getScope() {
      return myScope;
    }
  }
}
