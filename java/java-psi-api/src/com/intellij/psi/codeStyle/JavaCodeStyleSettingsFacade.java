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
import com.intellij.psi.PsiFile;

/**
 * @deprecated Use {@link JavaFileCodeStyleFacade} for per-file code-style-settings. Note: project settings
 * may not be applicable to a particular file.
 */
@Deprecated
public abstract class JavaCodeStyleSettingsFacade {

  /**
   * @deprecated Use {@link JavaFileCodeStyleFacade#useFQClassNames()}
   */
  @Deprecated
  public abstract boolean useFQClassNames();

  /**
   * @deprecated Use {@link JavaFileCodeStyleFacade#isGenerateFinalParameters()}
   */
  @Deprecated
  public abstract boolean isGenerateFinalParameters();

  /**
   * @deprecated Use {@link JavaFileCodeStyleFacade#forContext(PsiFile)} instead.
   */
  @Deprecated
  public static JavaCodeStyleSettingsFacade getInstance(Project project) {
    return ServiceManager.getService(project, JavaCodeStyleSettingsFacade.class);
  }
}
