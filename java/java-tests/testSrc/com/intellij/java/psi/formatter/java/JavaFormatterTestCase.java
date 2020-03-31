/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.psi.formatter.java;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.formatter.FormatterTestCase;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
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
