// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;


public class RenameJavaImplicitClassProcessor extends RenamePsiFileProcessor {

  @Override
  public boolean canProcessElement(@NotNull final PsiElement element) {
    return containsImplicitClass(element);
  }

  private static boolean containsImplicitClass(@NotNull PsiElement element) {
    return findImplicitClassInJavaFile(element) != null;
  }

  @Nullable
  private static PsiImplicitClass findImplicitClassInJavaFile(@NotNull PsiElement psiElement) {
    if (!(psiElement instanceof PsiJavaFile javaFile)) {
      return null;
    }
    PsiClass[] classes = javaFile.getClasses();
    if (classes.length != 1) {
      return null;
    }
    PsiClass aClass = classes[0];
    return (aClass instanceof PsiImplicitClass implicitClass) ? implicitClass : null;
  }

  @NotNull
  @Override
  public RenameDialog createRenameDialog(@NotNull Project project, @NotNull final PsiElement element, PsiElement nameSuggestionContext, Editor editor) {
    return new MyPsiFileRenameDialog(project, element, nameSuggestionContext, editor);
  }

  private static class MyPsiFileRenameDialog extends PsiFileRenameDialog {
    @NotNull
    private final PsiImplicitClass myImplicitClass;
    @Nullable
    private final String myExtension;

    private MyPsiFileRenameDialog(@NotNull Project project, @NotNull PsiElement element, PsiElement nameSuggestionContext, Editor editor) {
      super(project, element, nameSuggestionContext, editor);
      myImplicitClass = Objects.requireNonNull(findImplicitClassInJavaFile(element));
      myExtension = Optional.ofNullable(((PsiJavaFile)element).getVirtualFile())
        .map(file -> file.getExtension())
        .orElse(null);
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
