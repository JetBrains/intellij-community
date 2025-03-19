// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.generation.GenerateGetterAndSetterHandler;
import com.intellij.openapi.project.DumbAware;

/**
 * Action group which contains Generate... actions
 * Available in the Java code editor context only
 */
public class GenerateGetterAndSetterAction extends BaseGenerateAction implements DumbAware {
  public GenerateGetterAndSetterAction() {
    super(new GenerateGetterAndSetterHandler());
  }
}