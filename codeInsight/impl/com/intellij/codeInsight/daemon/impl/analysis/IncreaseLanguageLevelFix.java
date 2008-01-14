package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  private boolean isJdkSupportsLevel(Sdk jdk) {
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
    Module module = ModuleUtil.findModuleForFile(file.getVirtualFile(), project);
    return isJdkSupportsLevel(getRelevantJdk(project, module));
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    Module module = ModuleUtil.findModuleForFile(file.getVirtualFile(), project);
    LanguageLevel moduleLevel = module == null ? null : LanguageLevelModuleExtension.getInstance(module).getLanguageLevel();
    Sdk jdk = getRelevantJdk(project, module);
    if (moduleLevel != null && isJdkSupportsLevel(jdk)) {
      final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
      rootModel.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(myLevel);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          rootModel.commit();
        }
      });
    }
    else {
      LanguageLevelProjectExtension.getInstance(project).setLanguageLevel(myLevel);
    }
  }

  private static Sdk getRelevantJdk(final Project project, @Nullable Module module) {
    Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectJdk();
    Sdk moduleJdk = module == null ? null : ModuleRootManager.getInstance(module).getSdk();
    return moduleJdk == null ? projectJdk : moduleJdk;
  }

  public boolean startInWriteAction() {
    return false;
  }
}
