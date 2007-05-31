package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class IncreaseLanguageLevelFix implements IntentionAction {
  private final LanguageLevel myLevel;

  public IncreaseLanguageLevelFix(LanguageLevel targetLevel) {
    myLevel = targetLevel;
  }

  @NotNull
  public String getText() {
    return CodeInsightBundle.message("set.language.level.to.0", myLevel.getPresentableText());
  }

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("set.language.level");
  }

  private boolean isJdkSupportsLevel(ProjectJdk jdk) {
    final JavaSdk sdk = JavaSdk.getInstance();
    final String versionString = jdk.getVersionString();
    if (versionString == null) return false;
    String[] acceptableVersionNumbers = myLevel == LanguageLevel.JDK_1_3 ? new String[]{"1.3"} : myLevel == LanguageLevel.JDK_1_4 ? new String[]{"1.4"} : new String[]{"1.5", "5.0"};
    for (String number : acceptableVersionNumbers) {
      if (sdk.compareTo(versionString, number) >= 0) return true;
    }
    return false;
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    Module module = VfsUtil.getModuleForFile(project, file.getVirtualFile());
    return isJdkSupportsLevel(getRelevantJdk(project, module));
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    Module module = VfsUtil.getModuleForFile(project, file.getVirtualFile());
    LanguageLevel moduleLevel = module.getLanguageLevel();
    ProjectJdk jdk = getRelevantJdk(project, module);
    if (moduleLevel != null && isJdkSupportsLevel(jdk)) {
      ModuleRootManager.getInstance(module).setLanguageLevel(myLevel);
    }
    else {
      ProjectRootManagerEx.getInstanceEx(project).setLanguageLevel(myLevel);
    }
  }

  private static ProjectJdk getRelevantJdk(final Project project, final Module module) {
    ProjectJdk projectJdk = ProjectRootManager.getInstance(project).getProjectJdk();
    ProjectJdk moduleJdk = ModuleRootManager.getInstance(module).getJdk();
    return moduleJdk == null ? projectJdk : moduleJdk;
  }

  public boolean startInWriteAction() {
    return false;
  }
}
