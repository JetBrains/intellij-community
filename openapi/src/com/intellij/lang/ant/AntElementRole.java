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

import com.intellij.util.Icons;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author dyoma
 */
public enum AntElementRole {
  TARGET_ROLE(AntBundle.message("ant.role.ant.target"), Icons.ANT_TARGET_ICON),
  PROPERTY_ROLE(AntBundle.message("ant.role.ant.property"), Icons.PROPERTY_ICON),
  TASK_ROLE(AntBundle.message("ant.role.ant.task"), Icons.TASK_ICON),
  USER_TASK_ROLE(AntBundle.message("ant.element.role.user.task"), Icons.TASK_ICON),
  PROJECT_ROLE(AntBundle.message("ant.element.role.ant.project.name"), Icons.PROPERTY_ICON),
  MACRODEF_ROLE(AntBundle.message("ant.element.role.macrodef.element"), Icons.TASK_ICON),
  @NonNls NULL_ROLE("Ant element", null);

  AntElementRole(String name, Icon icon) {
    myName = name;
    myIcon = icon;
  }

  private final String myName;
  private final Icon myIcon;

  public String getName() {
    return myName;
  }

  public Icon getIcon() {
    return myIcon;
  }
}
