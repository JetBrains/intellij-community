// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.xml.converters.values;

import com.intellij.openapi.application.ApplicationManager;

public abstract class ClassArrayConverter extends ClassValueConverter {

   public static ClassArrayConverter getClassArrayConverter() {
     return ApplicationManager.getApplication().getService(ClassArrayConverter.class);
   }
}