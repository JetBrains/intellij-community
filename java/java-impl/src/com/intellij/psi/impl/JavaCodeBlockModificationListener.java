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
package com.intellij.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspXml.JspDirective;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;

public class JavaCodeBlockModificationListener implements PsiTreeChangePreprocessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.JavaCodeBlockModificationListener");

  private final PsiModificationTrackerImpl myModificationTracker;

  public JavaCodeBlockModificationListener(final PsiModificationTracker modificationTracker) {
    myModificationTracker = (PsiModificationTrackerImpl) modificationTracker;
  }

  @Override
  public void treeChanged(@NotNull final PsiTreeChangeEventImpl event) {
    switch (event.getCode()) {
      case BEFORE_CHILDREN_CHANGE:
      case BEFORE_PROPERTY_CHANGE:
      case BEFORE_CHILD_MOVEMENT:
      case BEFORE_CHILD_REPLACEMENT:
      case BEFORE_CHILD_ADDITION:
      case BEFORE_CHILD_REMOVAL:
        break;

      case CHILD_ADDED:
      case CHILD_REMOVED:
      case CHILD_REPLACED:
        processChange(event.getParent(), event.getOldChild(), event.getChild());
        break;

      case CHILDREN_CHANGED:
        // general childrenChanged() event after each change
        if (!event.isGenericChange()) {
          processChange(event.getParent(), event.getParent(), null);
        }
        break;

      case CHILD_MOVED:
      case PROPERTY_CHANGED:
        myModificationTracker.incCounter();
        break;

      default:
        LOG.error("Unknown code:" + event.getCode());
        break;
    }
  }

  private void processChange(final PsiElement parent, final PsiElement child1, final PsiElement child2) {
    try {
      if (!isInsideCodeBlock(parent)) {
        if (isClassOwner(parent.getContainingFile()) ||
            isClassOwner(child1) ||
            isClassOwner(child2) ||
            isSourceDir(parent) ||
            isClassOwner(parent.getParent())) {
          myModificationTracker.incCounter();
        }
        else {
          myModificationTracker.incOutOfCodeBlockModificationCounter();
        }
        return;
      }

      if (containsClassesInside(child1) || child2 != child1 && containsClassesInside(child2)) {
        myModificationTracker.incCounter();
      }
    }
    catch (PsiInvalidElementAccessException ignored) {
      myModificationTracker.incCounter(); // Shall not happen actually, just a pre-release paranoia
    }
  }

  private static boolean isSourceDir(PsiElement element) {
    return element instanceof PsiDirectory &&
           ProjectFileIndex.SERVICE.getInstance(element.getProject()).isInSource(((PsiDirectory)element).getVirtualFile());
  }

  private static boolean isClassOwner(final PsiElement element) {
    return element instanceof PsiClassOwner || element instanceof JspDirective;
  }

  private static boolean containsClassesInside(final PsiElement element) {
    if (element == null) return false;
    if (element instanceof PsiClass) return true;

    PsiElement child = element.getFirstChild();
    while (child != null) {
      if (containsClassesInside(child)) return true;
      child = child.getNextSibling();
    }

    return false;
  }

  private static boolean isInsideCodeBlock(PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      return false;
    }

    if (element == null || element.getParent() == null) return true;

    PsiElement parent = element;
    while (true) {
      if (parent instanceof PsiFile || parent instanceof PsiDirectory || parent == null) {
        return false;
      }
      if (parent instanceof PsiClass) return false; // anonymous or local class
      if (parent instanceof PsiModifiableCodeBlock) {
        if (!((PsiModifiableCodeBlock)parent).shouldChangeModificationCount(element)) {
          return true;
        }
      }
      parent = parent.getParent();
    }
  }
}
