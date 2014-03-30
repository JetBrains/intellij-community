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

/*
 * User: anna
 * Date: 15-May-2008
 */
package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.util.VariableData;

public abstract class AbstractExtractDialog extends DialogWrapper {
  protected AbstractExtractDialog(Project project) {
    super(project, true);
  }


  public abstract String getChosenMethodName();

  public abstract VariableData[] getChosenParameters();

  @PsiModifier.ModifierConstant
  public abstract String getVisibility();

  public abstract boolean isMakeStatic();

  public abstract boolean isChainedConstructor();
}