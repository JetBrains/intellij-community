package com.intellij.ide.structureView;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;

public interface StructureViewFactory {
  StructureView createStructureView(FileEditor fileEditor,
                                    StructureViewModel treeModel,
                                    Project project); 
}
