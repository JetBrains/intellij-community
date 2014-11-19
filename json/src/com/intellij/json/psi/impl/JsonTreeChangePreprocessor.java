package com.intellij.json.psi.impl;

import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JsonTreeChangePreprocessor implements PsiTreeChangePreprocessor {
  /**
   * @see com.intellij.psi.impl.PsiTreeChangePreprocessorBase#treeChanged(com.intellij.psi.impl.PsiTreeChangeEventImpl)
   */
  @Override
  public void treeChanged(@NotNull PsiTreeChangeEventImpl event) {
    if (!(event.getFile() instanceof JsonFile)) return;

    final PsiElement element = event.getParent();
    if (element == null || !(element.getManager() instanceof PsiManagerImpl)) {
      return;
    }
    final PsiModificationTrackerImpl modificationTracker = (PsiModificationTrackerImpl)element.getManager().getModificationTracker();
    boolean changedInsideCodeBlock = false;

    switch (event.getCode()) {
      case BEFORE_CHILDREN_CHANGE:
        if (event.getParent() instanceof PsiFile) {
          changedInsideCodeBlock = true;
          break;
        }

      case CHILDREN_CHANGED:
        if (event.isGenericChange()) {
          return;
        }
        changedInsideCodeBlock = isInsideCodeBlock(event.getParent());
        break;

      case BEFORE_CHILD_ADDITION:
      case BEFORE_CHILD_REMOVAL:
      case CHILD_ADDED:
      case CHILD_REMOVED:
        changedInsideCodeBlock = isInsideCodeBlock(event.getParent());
        break;

      case BEFORE_PROPERTY_CHANGE:
      case PROPERTY_CHANGED:
        changedInsideCodeBlock = false;
        break;

      case BEFORE_CHILD_REPLACEMENT:
      case CHILD_REPLACED:
        changedInsideCodeBlock = isInsideCodeBlock(event.getParent());
        break;

      case BEFORE_CHILD_MOVEMENT:
      case CHILD_MOVED:
        changedInsideCodeBlock = isInsideCodeBlock(event.getOldParent()) && isInsideCodeBlock(event.getNewParent());
        break;
    }

    if (!changedInsideCodeBlock) {
      modificationTracker.incOutOfCodeBlockModificationCounter();
    }
  }

  private boolean isInsideCodeBlock(@Nullable PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      return false;
    }
    if (element == null || element.getParent() == null) {
      return true;
    }
    // Consider all elements of JSON PSI as externally visible, i.e. they are out of code block
    return !(element.getLanguage() instanceof JsonLanguage);
  }
}
