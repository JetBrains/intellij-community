package com.intellij.ide.util;

import com.intellij.ide.structureView.StructureView;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class FileStructurePopupFactoryImpl implements FileStructurePopupFactory {
  @NotNull
  @Override
  public FileStructurePopup createFileStructurePopup(@NotNull Project project,
                                                     @NotNull FileEditor fileEditor,
                                                     @NotNull StructureView structureView,
                                                     boolean applySortAndFilter) {
    return new FileStructurePopup(project, fileEditor, structureView, applySortAndFilter);
  }
}
