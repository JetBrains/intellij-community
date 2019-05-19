/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public abstract class JavaCodeStyleSettingsFacade {
  public abstract int getNamesCountToUseImportOnDemand();

  public abstract boolean isToImportInDemand(String qualifiedName);
  
  public abstract boolean useFQClassNames();

  public abstract boolean isJavaDocLeadingAsterisksEnabled();

  public abstract int getIndentSize();

  public abstract boolean isGenerateFinalParameters();

  public abstract boolean isGenerateFinalLocals();


  public static JavaCodeStyleSettingsFacade getInstance(Project project) {
    return ServiceManager.getService(project, JavaCodeStyleSettingsFacade.class);
  }
}
