
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
