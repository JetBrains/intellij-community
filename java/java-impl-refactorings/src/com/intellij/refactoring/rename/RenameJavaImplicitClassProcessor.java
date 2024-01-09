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
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

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
                                         @Nullable PsiElement nameSuggestionContext,
                                         @Nullable Editor editor) {
    return new MyPsiFileRenameDialog(project, element, nameSuggestionContext, editor);
  }

  public static class MyPsiFileRenameDialog extends PsiFileRenameDialog {
    @Nullable
    private final String myExtension;

    private MyPsiFileRenameDialog(@NotNull Project project, @NotNull PsiElement element, PsiElement nameSuggestionContext, Editor editor) {
      super(project, element, nameSuggestionContext, editor);
      myExtension = Optional.ofNullable(((PsiJavaFile)element).getVirtualFile())
        .map(file -> file.getExtension())
        .orElse(null);
    }

    public PsiImplicitClass getImplicitClass() {
      return Objects.requireNonNull(JavaImplicitClassUtil.getImplicitClassFor(getPsiElement()));
    }

    @Override
    protected void canRun() throws ConfigurationException {
      String name = super.getNewName();
      if (Comparing.strEqual(name, getImplicitClass().getQualifiedName())) throw new ConfigurationException(null);
      if (!PsiNameHelper.getInstance(getProject()).isIdentifier(name)) {
        throw new ConfigurationException(LangBundle.message("dialog.message.valid.identifier", getNewName()));
      }
    }

    @Override
    protected String getFullName() {
      PsiImplicitClass implicitClass = getImplicitClass();
      String name = DescriptiveNameUtil.getDescriptiveName(implicitClass);
      String type = UsageViewUtil.getType(implicitClass);
      return StringUtil.isEmpty(name) ? type : type + " '" + name + "'";
    }

    @Override
    public String[] getSuggestedNames() {
      String name = getImplicitClass().getQualifiedName();
      if (name != null) {
        return new String[]{name};
      }
      return super.getSuggestedNames();
    }

    @VisibleForTesting
    @Override
    public NameSuggestionsField getNameSuggestionsField() {
      return super.getNameSuggestionsField();
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
