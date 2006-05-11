package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The base class for actions which create new file elements.
 *
 * @since 5.1
 */
public abstract class CreateElementActionBase extends AnAction {
  protected CreateElementActionBase(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  /**
   * @return created elements. Never null.
   */
  @NotNull protected abstract PsiElement[] invokeDialog(Project project, PsiDirectory directory);

  protected abstract void checkBeforeCreate(String newName, PsiDirectory directory) throws IncorrectOperationException;

  /**
   * @return created elements. Never null.
   */
  @NotNull protected abstract PsiElement[] create(String newName, PsiDirectory directory) throws Exception;

  protected abstract String getErrorTitle();

  protected abstract String getCommandName();

  protected abstract String getActionName(PsiDirectory directory, String newName);

  public final void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();

    final IdeView view = (IdeView)dataContext.getData(DataConstants.IDE_VIEW);
    if (view == null) {
      return;
    }

    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);

    final PsiDirectory dir = view.getOrChooseDirectory();
    if (dir == null) return;
    final PsiElement[] createdElements = invokeDialog(project, dir);

    for (PsiElement createdElement : createdElements) {
      view.selectElement(createdElement);
    }
  }

  public void update(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Presentation presentation = e.getPresentation();

    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    final IdeView view = (IdeView)dataContext.getData(DataConstants.IDE_VIEW);
    if (view == null || view.getDirectories().length == 0) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    presentation.setVisible(true);
    presentation.setEnabled(true);
  }

  protected static String filterMessage(String message) {
    if (message == null) return null;
    @NonNls final String ioExceptionPrefix = "java.io.IOException:";
    if (message.startsWith(ioExceptionPrefix)) {
      message = message.substring(ioExceptionPrefix.length());
    }
    return message;
  }

  protected class MyInputValidator implements InputValidator {
    private final Project myProject;
    private final PsiDirectory myDirectory;
    private SmartPsiElementPointer[] myCreatedElements;

    public MyInputValidator(final Project project, final PsiDirectory directory) {
      myProject = project;
      myDirectory = directory;
      myCreatedElements = new SmartPsiElementPointer[0];
    }

    public boolean checkInput(final String inputString) {
      return true;
    }

    public boolean canClose(final String inputString) {
      if (inputString.length() == 0) {
        Messages.showMessageDialog(myProject, IdeBundle.message("error.name.should.be.specified"),
                                   CommonBundle.getErrorTitle(), Messages.getErrorIcon());
        return false;
      }

      try {
        checkBeforeCreate(inputString, myDirectory);
      }
      catch (IncorrectOperationException e) {
        Messages.showMessageDialog(
          myProject,
          filterMessage(e.getMessage()),
          getErrorTitle(),
          Messages.getErrorIcon()
        );
        return false;
      }

      final LocalVcs lvcs = LocalVcs.getInstance(myProject);

      final Exception[] exception = new Exception[1];

      final Runnable command = new Runnable() {
        public void run() {
          final Runnable run = new Runnable() {
            public void run() {
              LvcsAction action = LvcsAction.EMPTY;
              try {
                action = lvcs.startAction(getActionName(myDirectory, inputString), "", false);
                PsiElement[] psiElements = create(inputString, myDirectory);
                myCreatedElements = new SmartPsiElementPointer[psiElements.length];
                SmartPointerManager manager = SmartPointerManager.getInstance(myProject);
                for (int i = 0; i < myCreatedElements.length; i++) {
                  myCreatedElements[i] = manager.createSmartPsiElementPointer(psiElements[i]);
                }
              }
              catch (Exception ex) {
                exception[0] = ex;
              }
              finally {
                action.finish();
              }
            }
          };
          ApplicationManager.getApplication().runWriteAction(run);
        }
      };
      CommandProcessor.getInstance().executeCommand(myProject, command, getCommandName(), null);

      if (exception[0] != null) {
        Messages.showMessageDialog(
          myProject,
          filterMessage(exception[0].getMessage()),
          getErrorTitle(),
          Messages.getErrorIcon()
        );

      }

      return myCreatedElements.length != 0;
    }

    public final PsiElement[] getCreatedElements() {
      List<PsiElement> elts = new ArrayList<PsiElement>();
      for (SmartPsiElementPointer pointer : myCreatedElements) {
        final PsiElement elt = pointer.getElement();
        if (elt != null) elts.add(elt);
      }
      return elts.toArray(new PsiElement[elts.size()]);
    }
  }
}
