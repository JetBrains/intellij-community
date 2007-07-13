package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * The standard "New Class" action.
 *
 * @since 5.1
 */
public class CreateClassAction extends CreateInPackageActionBase {
  public CreateClassAction() {
    super(IdeBundle.message("action.create.new.class"),
          IdeBundle.message("action.create.new.class"), Icons.CLASS_ICON);
  }

  @NotNull
  protected PsiElement[] invokeDialog(Project project, PsiDirectory directory) {
    CreateElementActionBase.MyInputValidator validator = new CreateElementActionBase.MyInputValidator(project, directory);
    Messages.showInputDialog(project, IdeBundle.message("prompt.enter.new.class.name"),
                             IdeBundle.message("title.new.class"), Messages.getQuestionIcon(), "", validator);
    return validator.getCreatedElements();
  }

  protected String getCommandName() {
    return IdeBundle.message("command.create.class");
  }


  protected String getErrorTitle() {
    return IdeBundle.message("title.cannot.create.class");
  }


  protected String getActionName(PsiDirectory directory, String newName) {
    return IdeBundle.message("progress.creating.class", directory.getPackage().getQualifiedName(), newName);
  }

  protected void doCheckCreate(final PsiDirectory dir, final String className) throws IncorrectOperationException {
    dir.checkCreateClass(className);
  }


  protected PsiClass doCreate(final PsiDirectory dir, final String className) throws IncorrectOperationException {
    return dir.createClass(className);
  }
}
