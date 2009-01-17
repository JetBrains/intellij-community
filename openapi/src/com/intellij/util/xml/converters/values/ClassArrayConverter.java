/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.converters.values;

import com.intellij.openapi.components.ServiceManager;

public abstract class ClassArrayConverter extends ClassValueConverter {

   public static ClassArrayConverter getClassArrayConverter() {
    return ServiceManager.getService(ClassArrayConverter.class);
  }
}