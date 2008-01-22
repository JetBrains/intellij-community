package com.intellij.ide;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.copy.CopyHandler;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class CopyPasteDelegator implements CopyPasteSupport {
  private final Project myProject;
  private final JComponent myKeyReceiver;
  private final MyEditable myEditable;

  public CopyPasteDelegator(Project project, JComponent keyReceiver) {
    myProject = project;
    myKeyReceiver = keyReceiver;
    myEditable = new MyEditable();
  }

  @NotNull
  protected abstract PsiElement[] getSelectedElements();

  @NotNull
  private PsiElement[] getValidSelectedElements() {
    PsiElement[] selectedElements = getSelectedElements();
    for (PsiElement element : selectedElements) {
      if (!element.isValid()) {
        return PsiElement.EMPTY_ARRAY;
      }
    }
    return selectedElements;
  }

  private void updateView() {
    myKeyReceiver.repaint();
  }

  public CopyProvider getCopyProvider() {
    return myEditable;
  }

  public CutProvider getCutProvider() {
    return myEditable;
  }

  public PasteProvider getPasteProvider() {
    return myEditable;
  }

  private class MyEditable implements CutProvider, CopyProvider, PasteProvider {
    public void performCopy(DataContext dataContext) {
      PsiElement[] elements = getValidSelectedElements();
      PsiCopyPasteManager.getInstance().setElements(elements, true);
      updateView();
    }

    public boolean isCopyEnabled(DataContext dataContext) {
      PsiElement[] elements = getValidSelectedElements();
      return CopyHandler.canCopy(elements);
    }

    public void performCut(DataContext dataContext) {
      PsiElement[] elements = getValidSelectedElements();
      if (MoveHandler.adjustForMove(myProject, elements, null) == null) {
        return;
      }
      // 'elements' passed instead of result of 'adjustForMove' because otherwise ProjectView would
      // not recognize adjusted elements when graying them
      PsiCopyPasteManager.getInstance().setElements(elements, false);
      updateView();
    }

    public boolean isCutEnabled(DataContext dataContext) {
      final PsiElement[] elements = getValidSelectedElements();
      return elements.length != 0 && MoveHandler.canMove(elements, null);
    }

    public void performPaste(DataContext dataContext) {
      final boolean[] isCopied = new boolean[1];
      final PsiElement[] elements = PsiCopyPasteManager.getInstance().getElements(isCopied);
      if (elements == null) return;
      try {
        PsiElement target = (PsiElement)dataContext.getData(DataConstantsEx.PASTE_TARGET_PSI_ELEMENT);
        if (isCopied[0]) {
          PsiDirectory targetDirectory = target instanceof PsiDirectory ? (PsiDirectory)target : null;
          if (targetDirectory == null && target instanceof PsiDirectoryContainer) {
            final PsiDirectory[] directories = ((PsiDirectoryContainer)target).getDirectories();
            if (directories.length > 0) {
              targetDirectory = directories[0];
            }
          }
          if (CopyHandler.canCopy(elements)) {
            CopyHandler.doCopy(elements, targetDirectory);
          }
        }
        else {
          if (MoveHandler.canMove(elements, target)) {
            MoveHandler.doMove(myProject, elements, target, new MoveCallback() {
              public void refactoringCompleted() {
                PsiCopyPasteManager.getInstance().clear();
              }
            });
          }
        }
      }
      catch (RuntimeException ex) {
        throw ex;
      }
      finally {
        updateView();
      }
    }

    public boolean isPastePossible(DataContext dataContext) {
      return true;
    }

    public boolean isPasteEnabled(DataContext dataContext){
      Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      if (project == null) {
        return false;
      }

      Object target = dataContext.getData(DataConstantsEx.PASTE_TARGET_PSI_ELEMENT);
      if (target == null) {
        return false;
      }
      PsiElement[] elements = PsiCopyPasteManager.getInstance().getElements(new boolean[]{false});
      if (elements == null) {
        return false;
      }

      // disable cross-project paste
      for (PsiElement element : elements) {
        PsiManager manager = element.getManager();
        if (manager == null || manager.getProject() != project) {
          return false;
        }
      }

      return true;
    }
  }
}