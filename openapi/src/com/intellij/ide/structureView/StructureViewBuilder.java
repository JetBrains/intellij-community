package com.intellij.ide.structureView;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;

public interface StructureViewBuilder {
  StructureView createStructureView(FileEditor fileEditor, Project project);
}
