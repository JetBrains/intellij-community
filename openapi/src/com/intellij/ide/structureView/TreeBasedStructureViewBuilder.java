package com.intellij.ide.structureView;

import com.intellij.peer.PeerFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.FileEditor;


public abstract class TreeBasedStructureViewBuilder implements StructureViewBuilder{
  public abstract StructureViewModel createStructureViewModel();

  public StructureView createStructureView(FileEditor fileEditor, Project project) {
    return PeerFactory.getInstance().getStructureVeiwFactory().createStructureView(fileEditor, createStructureViewModel(), project);
  }
}
