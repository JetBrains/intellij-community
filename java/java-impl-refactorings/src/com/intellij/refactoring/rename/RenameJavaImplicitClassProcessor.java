// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.lang.LangBundle;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.util.JavaImplicitClassUtil;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;


public class RenameJavaImplicitClassProcessor extends RenamePsiFileProcessor {

  @Override
  public boolean canProcessElement(@NotNull final PsiElement element) {
    return JavaImplicitClassUtil.isFileWithImplicitClass(element);
  }

  @NotNull
  @Override
  public RenameDialog createRenameDialog(@NotNull Project project,
                                         @NotNull final PsiElement element,
                                         PsiElement nameSuggestionContext,
                                         Editor editor) {
    return new MyPsiFileRenameDialog(project, element, nameSuggestionContext, editor);
  }

  private static class MyPsiFileRenameDialog extends PsiFileRenameDialog {
    @NotNull
    private final PsiImplicitClass myImplicitClass;
    @Nullable
    private final String myExtension;

    private MyPsiFileRenameDialog(@NotNull Project project, @NotNull PsiElement element, PsiElement nameSuggestionContext, Editor editor) {
      super(project, element, nameSuggestionContext, editor);
      myImplicitClass = Objects.requireNonNull(JavaImplicitClassUtil.getImplicitClassFor(element));
      myExtension = Optional.ofNullable(((PsiJavaFile)element).getVirtualFile())
        .map(file -> file.getExtension())
        .orElse(null);
    }

    @Override
    protected void canRun() throws ConfigurationException {
      String name = super.getNewName();
      if (Comparing.strEqual(name, myImplicitClass.getQualifiedName())) throw new ConfigurationException(null);
      if (!PsiNameHelper.getInstance(myImplicitClass.getProject()).isQualifiedName(name)) {
        throw new ConfigurationException(LangBundle.message("dialog.message.valid.identifier", getNewName()));
      }
    }

    @Override
    protected String getFullName() {
      String name = DescriptiveNameUtil.getDescriptiveName(myImplicitClass);
      String type = UsageViewUtil.getType(myImplicitClass);
      return StringUtil.isEmpty(name) ? type : type + " '" + name + "'";
    }

    @Override
    public String[] getSuggestedNames() {
      String name = myImplicitClass.getQualifiedName();
      if (name != null) {
        return new String[]{name};
      }
      return super.getSuggestedNames();
    }

    @Override
    public @NotNull String getNewName() {
      String name = super.getNewName();
      if (myExtension != null && !name.endsWith(myExtension)) {
        return name + "." + myExtension;
      }
      return name;
    }
  }
}
