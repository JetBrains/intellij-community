// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.actions;

import org.jetbrains.jps.model.java.JavaSourceRootType;


public class MarkTestSourceRootAction extends MarkSourceRootAction {
  public MarkTestSourceRootAction() {
    super(JavaSourceRootType.TEST_SOURCE);
  }
}
