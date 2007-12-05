package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

public class MoveClassToSeparateFileFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.MoveClassToSeparateFileFix");

  private final PsiClass myClass;

  public MoveClassToSeparateFileFix(PsiClass aClass) {
    myClass = aClass;
  }

  public String getText() {
    return QuickFixBundle.message("move.class.to.separate.file.text",
                                  myClass.getName());
  }

  public String getFamilyName() {
    return QuickFixBundle.message("move.class.to.separate.file.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if  (!myClass.isValid() || !myClass.getManager().isInProject(myClass)) return false;
    PsiDirectory dir = file.getContainingDirectory();
    if (dir == null) return false;
    try {
      if (myClass.isInterface()) {
        JavaDirectoryService.getInstance().checkCreateInterface(dir, myClass.getName());
      } else {
        JavaDirectoryService.getInstance().checkCreateClass(dir, myClass.getName());
      }
    }
    catch (IncorrectOperationException e) {
      return false;
    }

    return true;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myClass.getContainingFile())) return;

    PsiDirectory dir = file.getContainingDirectory();
    try{
      PsiClass placeHolder = myClass.isInterface() ? JavaDirectoryService.getInstance().createInterface(dir, myClass.getName()) : JavaDirectoryService.getInstance()
        .createClass(dir, myClass.getName());
      PsiClass newClass = (PsiClass)placeHolder.replace(myClass);
      myClass.delete();

      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, newClass.getContainingFile().getVirtualFile(), newClass.getTextOffset());
      FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

}
