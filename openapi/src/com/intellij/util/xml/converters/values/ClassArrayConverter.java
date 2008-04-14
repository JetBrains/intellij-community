/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.converters.values;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

public abstract class ClassArrayConverter extends ClassValueConverter {

   public static ClassArrayConverter getClassArrayConverter(Project project) {
    return ServiceManager.getService(project, ClassArrayConverter.class);
  }
}