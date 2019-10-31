// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.errorTreeView;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 */
public class GroupingElement extends ErrorTreeElement {
  private final String[] myText;
  private final Object myData;
  private final VirtualFile myFile;

  public GroupingElement(String name, Object data, VirtualFile file) {
    super(ErrorTreeElementKind.GENERIC);
    myText = new String[] {name};
    myData = data;
    myFile = file;
  }

  @Override
  public Object getData() {
    return myData;
  }

  @Override
  public String[] getText() {
    return myText;
  }

  public String getName() {
    return myText[0];
  }

  @Override
  public String getExportTextPrefix() {
    return "";
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public boolean isRenderWithBoldFont() {
    return true;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return myFile != null ? myFile.getFileType().getIcon() : AllIcons.FileTypes.Any_type;
  }
}
