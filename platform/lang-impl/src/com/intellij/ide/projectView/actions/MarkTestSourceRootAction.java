// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.actions;

import org.jetbrains.jps.model.java.JavaSourceRootType;


public class MarkTestSourceRootAction extends MarkSourceRootAction {
  public MarkTestSourceRootAction() {
    super(JavaSourceRootType.TEST_SOURCE);
  }
}
