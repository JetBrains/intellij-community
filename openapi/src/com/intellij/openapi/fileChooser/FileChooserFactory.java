/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

import java.awt.*;

public abstract class FileChooserFactory {
  public static FileChooserFactory getInstance() {
    return ApplicationManager.getApplication().getComponent(FileChooserFactory.class);
  }

  public abstract FileChooserDialog createFileChooser(FileChooserDescriptor descriptor, Project project);
  public abstract FileChooserDialog createFileChooser(FileChooserDescriptor descriptor, Component parent);
}