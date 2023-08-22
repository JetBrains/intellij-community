// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class SetupJDKFix implements IntentionAction, HighPriorityAction {
  private static final SetupJDKFix ourInstance = new SetupJDKFix();

  public static SetupJDKFix getInstance() {
    return ourInstance;
  }

  private SetupJDKFix() {
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("setup.jdk.location.text");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("setup.jdk.location.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, file.getResolveScope()) == null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, final PsiFile file) {
    SdkPopupFactory
      .newBuilder()
      .withProject(project)
      .withSdkTypeFilter(type -> type instanceof JavaSdkType)
      .updateSdkForFile(file)
      .buildPopup()
      .showInBestPositionFor(editor);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
