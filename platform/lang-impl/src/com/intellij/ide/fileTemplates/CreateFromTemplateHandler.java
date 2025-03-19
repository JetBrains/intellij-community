// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

/**
 * Allows for customizing behavior of creating new files from templates.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/using-file-templates.html#creating-new-files-from-template">Creating New Files from Template (IntelliJ Platform Docs)</a>
 */
public interface CreateFromTemplateHandler {
  ExtensionPointName<CreateFromTemplateHandler> EP_NAME = ExtensionPointName.create("com.intellij.createFromTemplateHandler");

  /**
   * @return {@code true} if this handler can handle a given template
   */
  boolean handlesTemplate(@NotNull FileTemplate template);

  /**
   * Creates a file from a template.
   *
   * @return the created PSI element.
   * It is usually PsiFile, but can be an element created in the file in case it needs additional validation or processing.
   */
  @NotNull
  PsiElement createFromTemplate(@NotNull Project project,
                                @NotNull PsiDirectory directory,
                                String fileName,
                                @NotNull FileTemplate template,
                                @NotNull String templateText,
                                @NotNull Map<String, Object> props) throws IncorrectOperationException;

  /**
   * @return {@code true} if this handler can create files in given directories
   */
  boolean canCreate(PsiDirectory @NotNull [] dirs);

  /**
   * Determines if the created file name is required.
   * If returned value is true and name is not provided, then create from template dialog will render a file name field.
   */
  boolean isNameRequired();

  /**
   * @return an error message displayed in the error dialog title when error occurred during file from template creation
   */
  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  String getErrorMessage();

  /**
   * Allows for extending template properties map.
   */
  void prepareProperties(@NotNull Map<String, Object> props);

  /**
   * Allows for extending template properties map.
   */
  default void prepareProperties(@NotNull Map<String, Object> props,
                                 String fileName,
                                 @NotNull FileTemplate template,
                                 @NotNull Project project) {}

  /**
   * @return command name used in the Undo/Redo UI elements
   */
  default @NotNull @Nls(capitalization = Nls.Capitalization.Title) String commandName(@NotNull FileTemplate template) {
    return IdeBundle.message("command.create.file.from.template");
  }
}
