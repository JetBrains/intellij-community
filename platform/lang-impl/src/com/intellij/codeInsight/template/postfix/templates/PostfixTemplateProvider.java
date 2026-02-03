// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateEditor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Extension point interface for providing language specific postfix templates.
 *
 * @see LanguagePostfixTemplate#EP_NAME
 * @see PostfixTemplate
 * @see <a href=https://plugins.jetbrains.com/docs/intellij/postfix-completion.html">Postfix Completion (IntelliJ Platform Docs)</a>
 */
public interface PostfixTemplateProvider {

  /**
   * @return identifier used for storing settings of this provider's templates
   */
  default @NotNull @NonNls String getId() {
    return getClass().getName();
  }

  /**
   * @return presentation name of an editable template type. If {@code null}, the provider doesn't allow customizing its templates.
   */
  default @Nullable @NlsActions.ActionText String getPresentableName() {
    return null;
  }

  /**
   * @return built-in templates registered in the provider in their original state.
   * Consider using {@link PostfixTemplatesUtils#getAvailableTemplates(PostfixTemplateProvider)} for actually enabled templates.
   */
  @NotNull
  Set<PostfixTemplate> getTemplates();

  /**
   * @return {@code true} if a given symbol can separate template keys
   */
  boolean isTerminalSymbol(char currentChar);

  /**
   * Prepares a file for template expanding,
   * e.g. a Java postfix template adds a semicolon after caret in order to simplify context checking.
   * <p>
   * File content doesn't contain template's key, it is deleted just before this method invocation.
   * <p>
   * Running on EDT.
   * <p>
   * Note that when postfix template's availability is checked, the {@code file} parameter is a COPY of the actual file,
   * so you can do with it anything that you want.
   * At the same time, it is not recommended to modify the editor state because it's real.
   */
  void preExpand(@NotNull PsiFile file, @NotNull Editor editor);

  /**
   * Cleans up a file content after template expanding is finished (doesn't matter if it finished successfully or not),
   * e.g. a Java postfix template cleans a semicolon inserted in {@link #preExpand(PsiFile, Editor)}.
   */
  void afterExpand(@NotNull PsiFile file, @NotNull Editor editor);

  /**
   * Prepares a file for checking the availability of templates.
   * Almost the same as {@link #preExpand(PsiFile, Editor)} with several differences:
   * <ol>
   * <li>Processes copy of the file. So implementations can modify it without corrupting the real file.
   * <li>Could be invoked from anywhere (EDT, write-action, read-action, completion-thread, etc.) so implementations should make
   * additional effort to make changes in the file.
   * </ol>
   * <p>
   * File content doesn't contain template's key, it is deleted just before this method invocation.
   * <p>
   * NOTE: editor is real (not a copy) and it doesn't represent the {@code copyFile}.
   * It's safer to use {@code currentOffset} parameter instead of the editor offset.
   * Do not modify text with the provided {@code realEditor}.
   */
  @NotNull
  PsiFile preCheck(@NotNull PsiFile copyFile, @NotNull Editor realEditor, int currentOffset);

  /**
   * Returns the editor that is used to represent a template in UI and create the template from the settings provided by users.
   * <p>
   * If {@code templateToEdit} is {@code null}, it's considered as an editor for a new template.
   */
  default @Nullable PostfixTemplateEditor createEditor(@Nullable PostfixTemplate templateToEdit) {
    return null;
  }

  /**
   * Reads from a given DOM element, and instantiates a template that was stored by this provider.
   */
  default @Nullable PostfixTemplate readExternalTemplate(@NotNull @NonNls String id, @NotNull @NlsSafe String name, @NotNull Element template) {
    return null;
  }

  /**
   * Stores a given template that was created by this provider to a given parent DOM element.
   */
  default void writeExternalTemplate(@NotNull PostfixTemplate template, @NotNull Element parentElement) {
  }
}
