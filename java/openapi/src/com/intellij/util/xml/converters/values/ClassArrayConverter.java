// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.converters.values;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.xml.ConverterManager;

public abstract class ClassArrayConverter extends ClassValueConverter {

  public static ClassArrayConverter getClassArrayConverter() {
    ConverterManager converterManager = ApplicationManager.getApplication().getService(ConverterManager.class);
    return (ClassArrayConverter)converterManager.getConverterInstance(ClassArrayConverter.class);
  }
}