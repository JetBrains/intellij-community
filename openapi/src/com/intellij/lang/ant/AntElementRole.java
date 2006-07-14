/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.ant;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Icons;

import javax.swing.*;

/**
 * @author dyoma
 */
public class AntElementRole {
  public static final Icon TASK_ICON = IconLoader.getIcon("/ant/task.png");
  private final String myName;
  private final Icon myIcon;
  public static final AntElementRole TARGET_ROLE = new AntElementRole(AntBundle.message("ant.role.ant.target"), Icons.ANT_TARGET_ICON);
  public static final AntElementRole PROPERTY_ROLE = new AntElementRole(AntBundle.message("ant.role.ant.property"), Icons.PROPERTY_ICON);
  public static final AntElementRole TASK_ROLE = new AntElementRole(AntBundle.message("ant.role.ant.task"), TASK_ICON);

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
