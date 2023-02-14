// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.java;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.formatter.FormatterTestCase;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;


public abstract class JavaFormatterTestCase extends FormatterTestCase {
  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17();
  }

  @NotNull
  @Override
  protected String getModuleTypeId() {
    return ModuleTypeId.JAVA_MODULE;
  }

  @Override
  protected String getFileExtension() {
    return "java";
  }

  protected JavaCodeStyleSettings getCustomJavaSettings() {
    return JavaCodeStyleSettings.getInstance(getProject());
  }
}
