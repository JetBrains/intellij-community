// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.lw.LwRootContainer;

public interface NestedFormLoader {
  LwRootContainer loadForm(String formFileName) throws Exception;
  String getClassToBindName(LwRootContainer container);
}
