package com.intellij.platform.renameProject;

import com.intellij.ide.TitledHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.PsiElementRenameHandler;

/**
 * @author lene
 */
public class ProjectFolderRenameHandler extends PsiElementRenameHandler implements TitledHandler {
  @Override
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    return RenameProjectHandler.isAvailable(dataContext) && super.isAvailableOnDataContext(dataContext);
  }

  @Override
  public boolean isRenaming(DataContext dataContext) {
    return RenameProjectHandler.isAvailable(dataContext) && super.isRenaming(dataContext);
  }

  @Override
  public String getActionTitle() {
    return RefactoringBundle.message("rename.directory.title");
  }
}
