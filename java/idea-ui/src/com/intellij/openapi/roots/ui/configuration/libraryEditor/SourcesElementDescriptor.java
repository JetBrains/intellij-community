package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.project.ProjectBundle;

import javax.swing.*;

class SourcesElementDescriptor extends NodeDescriptor<SourcesElement> {
    private final SourcesElement myElement;
    private static final Icon ICON = IconLoader.getIcon("/nodes/sourceFolder.png");

    public SourcesElementDescriptor(NodeDescriptor parentDescriptor, SourcesElement element) {
      super(null, parentDescriptor);
      myElement = element;
      myOpenIcon = myClosedIcon = ICON;
    }

    public boolean update() {
      myName = ProjectBundle.message("library.sources.node");
      return false;
    }

    public SourcesElement getElement() {
      return myElement;
    }
  }
