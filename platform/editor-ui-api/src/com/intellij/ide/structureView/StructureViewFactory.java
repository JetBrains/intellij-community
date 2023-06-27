// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Factory interface for creating instances of the standard structure view component.
 */
public abstract class StructureViewFactory {
  /**
   * Creates a structure view component instance for the specified editor.
   *
   * @param fileEditor the editor to which the structure view is linked.
   * @param treeModel  the model defining the data shown in the structure view.
   * @param project    the project containing the file for which the structure view is requested.
   * @return the structure view instance.
   */
  public abstract @NotNull StructureView createStructureView(FileEditor fileEditor,
                                                    @NotNull StructureViewModel treeModel,
                                                    @NotNull Project project);

  /**
   * Creates a structure view component instance for the specified editor.
   *
   * @param fileEditor the editor to which the structure view is linked.
   * @param treeModel  the model defining the data shown in the structure view.
   * @param project    the project containing the file for which the structure view is requested.
   * @param showRootNode pass {@code false} if root node of the structure built should not actually be shown in result tree.
   * @return the structure view instance.
   */
  public abstract @NotNull StructureView createStructureView(FileEditor fileEditor,
                                                    @NotNull StructureViewModel treeModel,
                                                    @NotNull Project project,
                                                    boolean showRootNode);

  public static StructureViewFactory getInstance(Project project) {
    return project.getService(StructureViewFactory.class);
  }
}
