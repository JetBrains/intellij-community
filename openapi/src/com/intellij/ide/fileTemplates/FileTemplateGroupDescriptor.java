/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.fileTemplates;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class FileTemplateGroupDescriptor extends FileTemplateDescriptor {
  private String myTitle;
  private List<FileTemplateDescriptor> myTemplates = new ArrayList<FileTemplateDescriptor>();

  public FileTemplateGroupDescriptor(String title, Icon icon) {
    super(null, icon);
    myTitle = title;
  }

  public String getTitle() {
    return myTitle;
  }

  public FileTemplateDescriptor[] getTemplates() {
    return myTemplates.toArray(new FileTemplateDescriptor[myTemplates.size()]);
  }

  public void addTemplate(FileTemplateDescriptor descriptor) {
    myTemplates.add(descriptor);
  }
}