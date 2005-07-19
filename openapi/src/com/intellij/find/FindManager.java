/*
* Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.find;

import com.intellij.aspects.psi.PsiPointcut;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

public abstract class FindManager {
  public static FindManager getInstance(Project project) {
    return project.getComponent(FindManager.class);
  }

  public abstract boolean showFindDialog(FindModel model);

  public abstract int showPromptDialog(FindModel model, String title);

  public abstract FindModel getFindInFileModel();

  public abstract FindModel getFindInProjectModel();

  public abstract FindResult findString(CharSequence text, int offset, FindModel model);

  public abstract String getStringToReplace(String foundString, FindModel model);

  public abstract boolean findWasPerformed();

  public abstract void setFindWasPerformed();

  public abstract void setFindNextModel(FindModel model);

  public abstract FindModel getFindNextModel();

  public abstract boolean canFindUsages(PsiElement element);

  /**
   */ 
  public abstract void findUsages(PsiElement element);

  public abstract void findJoinpointsByPointcut(PsiPointcut pointcut);

  public abstract void findUsagesInEditor(PsiElement element, FileEditor editor);
  public abstract boolean findNextUsageInEditor(FileEditor editor);
  public abstract boolean findPreviousUsageInEditor(FileEditor editor);
}
