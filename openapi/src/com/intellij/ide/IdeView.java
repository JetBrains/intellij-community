package com.intellij.ide;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Common interface for IDE views where files can be selected (project view, packages view,
 * favorites or commander).
 *
 * @since 5.1
 * @see com.intellij.openapi.actionSystem.DataConstants#IDE_VIEW
 */
public interface IdeView {
  /**
   * Selects the specified element in the view.
   *
   * @param element the element to select.
   */
  void selectElement(PsiElement element);

  /**
   * Returns the list of directories corresponding to the element currently selected in the view.
   * Can return a list of multiple elements if a package is selected.
   *
   * @return the list of directories, or an empty array if nothing is selected.
   */
  PsiDirectory[] getDirectories();

  /**
   * Returns the directory for the element currently selected in the view. If multiple directories
   * correspond to the selected element, shows a popup allowing the user to choose one of them.
   *
   * @return the selected directory, or null if there is no selection or the popup was cancelled.
   */
  @Nullable
  PsiDirectory getOrChooseDirectory();
}
