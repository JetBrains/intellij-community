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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiQualifiedNamedElement;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerComposite;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: Jul 26, 2010
 */
public class RefactoringScopeElementListenerProvider implements RefactoringElementListenerProvider {
  private static final Logger LOG = Logger.getInstance("#" + RefactoringScopeElementListenerProvider.class.getName());

  @Override
  public RefactoringElementListener getListener(PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (!(element instanceof PsiQualifiedNamedElement)) return null;
    final String oldName = ((PsiQualifiedNamedElement)element).getQualifiedName();
    RefactoringElementListenerComposite composite = null;
    for (final NamedScopesHolder holder : NamedScopeManager.getAllNamedScopeHolders(element.getProject())) {
      final NamedScope[] scopes = holder.getEditableScopes();
      for (int i = 0; i < scopes.length; i++) {
        final NamedScope scope = scopes[i];
        final PackageSet packageSet = scope.getValue();
        if (packageSet != null && (containingFile == null || packageSet.contains(containingFile, holder))) {
          composite = traverse(new OldScopeDescriptor(oldName, scope, i, holder), composite, packageSet);
        }
      }
    }
    return composite;
  }

  private static RefactoringElementListenerComposite traverse(OldScopeDescriptor scopeDescriptor,
                                                              RefactoringElementListenerComposite composite,
                                                              PackageSet packageSet) {
    if (packageSet instanceof PatternBasedPackageSet) {
      composite = checkPatternPackageSet(scopeDescriptor, composite, ((PatternBasedPackageSet)packageSet),
                                         scopeDescriptor.getScope().getValue().getText());
    }
    else if (packageSet instanceof ComplementPackageSet) {
      composite = traverse(scopeDescriptor, composite, ((ComplementPackageSet)packageSet).getComplementarySet());
    }
    else if (packageSet instanceof UnionPackageSet) {
      composite = traverse(scopeDescriptor, composite, ((UnionPackageSet)packageSet).getFirstSet());
      composite = traverse(scopeDescriptor, composite, ((UnionPackageSet)packageSet).getSecondSet());
    }
    else if (packageSet instanceof IntersectionPackageSet) {
      composite = traverse(scopeDescriptor, composite, ((IntersectionPackageSet)packageSet).getFirstSet());
      composite = traverse(scopeDescriptor, composite, ((IntersectionPackageSet)packageSet).getSecondSet());
    }
    return composite;
  }

  private static RefactoringElementListenerComposite checkPatternPackageSet(final OldScopeDescriptor descriptor,
                                                                            RefactoringElementListenerComposite composite,
                                                                            final PatternBasedPackageSet pattern,
                                                                            final String text) {
    if (pattern.isOn(descriptor.getOldQName())) {
      if (composite == null) {
        composite = new RefactoringElementListenerComposite();
      }
      composite.addListener(new RefactoringElementListener() {
        @Override
        public void elementMoved(@NotNull PsiElement newElement) {
          setName(newElement);
        }

        @Override
        public void elementRenamed(@NotNull PsiElement newElement) {
          setName(newElement);
        }

        private void setName(@NotNull PsiElement newElement) {
          LOG.assertTrue(newElement instanceof PsiQualifiedNamedElement);
          try {
            final String newPattern = text.replace(descriptor.getOldQName(), ((PsiQualifiedNamedElement)newElement).getQualifiedName());
            final PackageSet newSet = PackageSetFactory.getInstance().compile(newPattern);
            NamedScope newScope = new NamedScope(descriptor.getScope().getName(), newSet);
            final NamedScope[] currentScopes = descriptor.getHolder().getEditableScopes();
            currentScopes[descriptor.getIdx()] = newScope;
            descriptor.getHolder().setScopes(currentScopes);
          }
          catch (ParsingException ignore) {
          }
        }
      });
    }
    return composite;
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
