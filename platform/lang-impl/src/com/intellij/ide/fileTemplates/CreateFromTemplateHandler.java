// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.fileTemplates;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;


public interface CreateFromTemplateHandler {
  ExtensionPointName<CreateFromTemplateHandler> EP_NAME = ExtensionPointName.create("com.intellij.createFromTemplateHandler");

  boolean handlesTemplate(@NotNull FileTemplate template);

  @NotNull
  PsiElement createFromTemplate(@NotNull Project project,
                                @NotNull PsiDirectory directory,
                                String fileName,
                                @NotNull FileTemplate template,
                                @NotNull String templateText,
                                @NotNull Map<String, Object> props) throws IncorrectOperationException;

  boolean canCreate(PsiDirectory @NotNull [] dirs);

  boolean isNameRequired();

  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  String getErrorMessage();

  void prepareProperties(@NotNull Map<String, Object> props);

  default void prepareProperties(@NotNull Map<String, Object> props,
                                 String fileName,
                                 @NotNull FileTemplate template,
                                 @NotNull Project project) {}

  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  default String commandName(@NotNull FileTemplate template) {
    return IdeBundle.message("command.create.file.from.template");
  }
}