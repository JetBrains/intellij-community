/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.fileTemplates;

import javax.swing.*;

public class FileTemplateDescriptor {
  private Icon myIcon;
  private String myFileName;

  public FileTemplateDescriptor(String fileName, Icon icon) {
    myIcon = icon;
    myFileName = fileName;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public String getFileName() {
    return myFileName;
  }
}