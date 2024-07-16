// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.java;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.formatter.FormatterTestCase;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.workspaceModel.ide.legacyBridge.impl.java.JavaModuleTypeUtils.JAVA_MODULE_ENTITY_TYPE_ID_NAME;

public abstract class JavaFormatterTestCase extends FormatterTestCase {
  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17();
  }

  @NotNull
  @Override
  protected String getModuleTypeId() {
    return JAVA_MODULE_ENTITY_TYPE_ID_NAME;
  }

  @Override
  protected String getFileExtension() {
    return "java";
  }

  protected JavaCodeStyleSettings getCustomJavaSettings() {
    return JavaCodeStyleSettings.getInstance(getProject());
  }
}
