// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Default implementation of the {@link StructureViewBuilder} interface which uses the
 * standard implementation of the {@link StructureView} component and allows to
 * customize the data displayed in the structure view.
 *
 * @see StructureViewModel
 * @see TextEditorBasedStructureViewModel
 * @see com.intellij.lang.LanguageStructureViewBuilder#getStructureViewBuilder(com.intellij.psi.PsiFile)
 */
public abstract class TreeBasedStructureViewBuilder implements StructureViewBuilder {
  /**
   * Returns the structure view model defining the data displayed in the structure view
   * for a specific file.
   *
   * @return the structure view model instance.
   * @see TextEditorBasedStructureViewModel
   */
  @NotNull
  public abstract StructureViewModel createStructureViewModel(@Nullable Editor editor);

  @Override
  @NotNull
  public StructureView createStructureView(FileEditor fileEditor, @NotNull Project project) {
    StructureViewModel model = createStructureViewModel(fileEditor instanceof TextEditor ? ((TextEditor)fileEditor).getEditor() : null);
    StructureView view = StructureViewFactory.getInstance(project).createStructureView(fileEditor, model, project, isRootNodeShown());
    Disposer.register(view, model);
    return view;
  }

  /**
   * Override returning {@code false} if root node created by {@link #createStructureViewModel(Editor editor)} shall not be visible
   * @return {@code false} if root node shall not be visible in structure tree.
   */
  public boolean isRootNodeShown() {
    return true;
  }
}
