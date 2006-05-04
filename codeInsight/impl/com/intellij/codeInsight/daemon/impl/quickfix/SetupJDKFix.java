/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 20, 2002
 * Time: 8:49:24 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.util.projectWizard.JdkChooserPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

public class SetupJDKFix implements IntentionAction {
  private static final SetupJDKFix ourInstance = new SetupJDKFix();
  public static SetupJDKFix getInstnace() {
    return ourInstance;
  }

  private SetupJDKFix() {
  }

  public String getText() {
    return QuickFixBundle.message("setup.jdk.location.text");
  }

  public String getFamilyName() {
    return QuickFixBundle.message("setup.jdk.location.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return PsiManager.getInstance(project).findClass("java.lang.Object",file.getResolveScope()) == null;
  }

  public void invoke(Project project, Editor editor, final PsiFile file) {
    ProjectJdk projectJdk = JdkChooserPanel.chooseAndSetJDK(project);
    if (projectJdk == null) return;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        Module module = ModuleUtil.findModuleForPsiElement(file);
        if (module != null) {
          ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
          modifiableModel.inheritJdk();
          modifiableModel.commit();
        }
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }
}
