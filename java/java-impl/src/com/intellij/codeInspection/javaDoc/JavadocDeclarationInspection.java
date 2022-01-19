// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JavadocDeclarationInspection extends LocalInspectionTool {

  public String additionalJavadocTags = "";
  public boolean ignoreDuplicatedThrows = true;
  public boolean ignoreJavaDocPeriod = true;
  public boolean ignorePointToItself = false;

  @Override
  public @Nullable JComponent createOptionsPanel() {
    return JavadocUIUtil.INSTANCE.javadocDeclarationOptions(this);
  }

}