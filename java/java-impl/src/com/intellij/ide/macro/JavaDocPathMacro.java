/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.javadoc.JavadocConfiguration;
import com.intellij.javadoc.JavadocGenerationManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;

import java.io.File;

public final class JavaDocPathMacro extends Macro {
  public String getName() {
    return "JavaDocPath";
  }

  public String getDescription() {
    return IdeBundle.message("macro.javadoc.output.directory");
  }

  public String expand(DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return null;
    }
    JavadocGenerationManager manager = project.getComponent(JavadocGenerationManager.class);
    if (manager == null) {
      return null;
    }
    final JavadocConfiguration configuration = manager.getConfiguration();
    return configuration.OUTPUT_DIRECTORY == null ? null : configuration.OUTPUT_DIRECTORY.replace('/', File.separatorChar);
  }
}
