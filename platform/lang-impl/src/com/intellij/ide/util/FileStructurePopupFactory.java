package com.intellij.ide.util;

import com.intellij.ide.structureView.StructureView;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface FileStructurePopupFactory {

  @NotNull
  FileStructurePopup createFileStructurePopup(@NotNull Project project,
                                              @NotNull FileEditor fileEditor,
                                              @NotNull StructureView structureView,
                                              final boolean applySortAndFilter);

  class SERVICE {
    public static FileStructurePopupFactory getInstance() {
      return ServiceManager.getService(FileStructurePopupFactory.class);
    }
  }
}
