/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ant;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author dyoma
 */
public class AntElementRole {
  public static final Icon PROPERTY_ICON = IconLoader.getIcon("/nodes/property.png");
  public static final Icon TARGET_ICON = IconLoader.getIcon("/ant/target.png");
  public static final Icon TASK_ICON = IconLoader.getIcon("/ant/task.png");
  private final String myName;
  private final Icon myIcon;
  public static final AntElementRole TARGET_ROLE = new AntElementRole("Ant target", TARGET_ICON);
  public static final AntElementRole PROPERTY_ROLE = new AntElementRole("Ant property", PROPERTY_ICON);
  public static final AntElementRole TASK_ROLE = new AntElementRole("Ant task", TASK_ICON);

  public AntElementRole(String name, Icon icon) {
    myName = name;
    myIcon = icon;
  }

  public String getName() {
    return myName;
  }

  public Icon getIcon() {
    return myIcon;
  }
}
