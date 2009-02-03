/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.psi;

import java.util.List;

/**
 * Represents a PSI element visitor which recursively visits the children of the element
 * on which the visit was started.
 */
public abstract class PsiRecursiveElementWalkingVisitor extends PsiElementVisitor {
  private final boolean myVisitAllFileRoots;
  private boolean isDown;
  private boolean startedWalking;

  protected PsiRecursiveElementWalkingVisitor() {
    this(false);
  }

  protected PsiRecursiveElementWalkingVisitor(boolean visitAllFileRoots) {
    myVisitAllFileRoots = visitAllFileRoots;
  }

  private void walk(PsiElement root) {
    for (PsiElement element = next(root, root); element != null; element = next(element, root)) {
      isDown = false; // if client visitor did not call default visitElement it means skip subtree
      PsiElement parent = element.getParent();
      PsiElement next = element.getNextSibling();
      element.accept(this);
      assert element.getNextSibling() == next;
      assert element.getParent() == parent;
    }
    startedWalking = false;
  }

  private PsiElement next(PsiElement element, PsiElement root) {
    if (isDown) {
      PsiElement child = element.getFirstChild();
      if (child != null) return child;
    }
    else {
      isDown = true;
    }
    // up
    while (element != root) {
      PsiElement next = element.getNextSibling();
      if (next != null) return next;
      element = element.getParent();
    }
    return null;
  }

  public void visitElement(final PsiElement element) {
    isDown = true;
    if (!startedWalking) {
      startedWalking = true;
      walk(element);
    }
  }

  @Override
  public void visitFile(final PsiFile file) {
    if (myVisitAllFileRoots) {
      final FileViewProvider viewProvider = file.getViewProvider();
      final List<PsiFile> allFiles = viewProvider.getAllFiles();
      if (allFiles.size() > 1) {
        if (file == viewProvider.getPsi(viewProvider.getBaseLanguage())) {
          for (PsiFile lFile : allFiles) {
            lFile.acceptChildren(this);
            startedWalking = false;
          }
          return;
        }
      }
    }

    super.visitFile(file);
  }
}