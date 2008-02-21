package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
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
import org.jetbrains.annotations.NotNull;

public class MoveClassToSeparateFileFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.MoveClassToSeparateFileFix");

  private final PsiClass myClass;

  public MoveClassToSeparateFileFix(PsiClass aClass) {
    myClass = aClass;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("move.class.to.separate.file.text",
                                  myClass.getName());
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("move.class.to.separate.file.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if  (!myClass.isValid() || !myClass.getManager().isInProject(myClass)) return false;
    PsiDirectory dir = file.getContainingDirectory();
    if (dir == null) return false;
    try {
      String name = myClass.getName();
      if (myClass.isInterface()) {
        JavaDirectoryService.getInstance().checkCreateInterface(dir, name);
      }
      else {
        JavaDirectoryService.getInstance().checkCreateClass(dir, name);
      }
    }
    catch (IncorrectOperationException e) {
      return false;
    }

    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myClass.getContainingFile())) return;

    PsiDirectory dir = file.getContainingDirectory();
    try{
      String name = myClass.getName();
      PsiClass placeHolder = myClass.isInterface() ? JavaDirectoryService.getInstance().createInterface(dir, name) :
                             JavaDirectoryService.getInstance().createClass(dir, name);
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
