package com.intellij.openapi.fileChooser.ex;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;

public class FileNodeDescriptor extends NodeDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.chooser.FileNodeDescriptor");

  private FileElement myFileElement;
  private Icon myOriginalOpenIcon;
  private Icon myOriginalClosedIcon;
  private final String myComment;

  public FileNodeDescriptor(Project project,
                            FileElement element,
                            NodeDescriptor parentDescriptor,
                            Icon openIcon,
                            Icon closedIcon,
                            String name,
                            String comment) {
    super(project, parentDescriptor);
    myOriginalOpenIcon = openIcon;
    myOriginalClosedIcon = closedIcon;
    myComment = comment;
    LOG.assertTrue(element != null);
    myFileElement = element;
    myName = name;
  }

  public boolean update() {
    boolean changed = false;

    // special handling for roots with names (e.g. web roots)
    if (myName == null || myComment == null) {
      final String newName = myFileElement.toString();
      if (!newName.equals(myName)) changed = true;
      myName = newName;
    }

    VirtualFile file = myFileElement.getFile();

    if (file == null) return true;

    myOpenIcon = myOriginalOpenIcon;
    myClosedIcon = myOriginalClosedIcon;
    if (myFileElement.isHidden()) {
      myOpenIcon = IconLoader.getTransparentIcon(myOpenIcon);
      myClosedIcon = IconLoader.getTransparentIcon(myClosedIcon);
    }
    myColor = myFileElement.isHidden() ? SimpleTextAttributes.DARK_TEXT.getFgColor() : null;
    return changed;
  }

  @NotNull
  public final FileElement getElement() {
    return myFileElement;
  }

  protected final void setElement(FileElement descriptor) {
    myFileElement = descriptor;
  }

  public String getComment() {
    return myComment;
  }
}
