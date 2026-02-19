// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateMetaData;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

/**
 * Represents a postfix template.
 * Postfix template is a live template that is applicable to a specific code fragment, e.g. "sout" template:
 * <br>
 * <code>
 * "hello".sout
 * </code>
 * <br>
 * is expanded to:
 * <br>
 * <code>
 * System.out.println("hello");
 * </code>
 * <p>
 * Editable postfix template MUST:
 * <ul>
 * <li>know the provider that created it</li>
 * <li>provide proper {@code equals()}/{@code hashCode()} implementation</li>
 * </ul>
 * Equal postfix templates produced by the very same provider will overwrite each other.
 *
 * @see PostfixTemplateProvider
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/postfix-templates.html">Postfix Templates (IntelliJ Platform Docs)</a>
 */
public abstract class PostfixTemplate implements PossiblyDumbAware {
  private final @NotNull @NonNls String myId;
  private final @NotNull @NlsSafe String myPresentableName;
  private final @NotNull @NlsSafe String myKey;
  private final @NotNull NotNullLazyValue<@NlsContexts.DetailedDescription String> myLazyDescription =
    NotNullLazyValue.createValue(() -> calcDescription());

  private final @NotNull @NlsSafe String myExample;
  private final @Nullable PostfixTemplateProvider myProvider;

  /**
   * @deprecated use {@link #PostfixTemplate(String, String, String, PostfixTemplateProvider)}
   */
  @Deprecated(forRemoval = true)
  protected PostfixTemplate(@NotNull @NlsSafe String name, @NotNull @NlsSafe String example) {
    this(null, name, "." + name, example, null);
  }

  protected PostfixTemplate(@Nullable @NonNls String id,
                            @NotNull @NlsSafe String name,
                            @NotNull @NlsSafe String example,
                            @Nullable PostfixTemplateProvider provider) {
    this(id, name, "." + name, example, provider);
  }

  protected PostfixTemplate(@Nullable String id,
                            @NotNull String name,
                            @NotNull String key,
                            @NotNull String example,
                            @Nullable PostfixTemplateProvider provider) {
    myId = id != null ? id : getClass().getName() + "#" + key;
    myPresentableName = name;
    myKey = key;
    myExample = example;
    myProvider = provider;
  }

  protected @NotNull @NlsContexts.DetailedDescription String calcDescription() {
    String defaultDescription = CodeInsightBundle.message("postfix.template.description.under.construction");
    try {
      return PostfixTemplateMetaData.createMetaData(this).getDescription().getText();
    }
    catch (IOException e) {
      //ignore
    }

    return defaultDescription;
  }

  /**
   * @return identifier used for saving the settings related to this template
   */
  public @NotNull @NonNls String getId() {
    return myId;
  }

  /**
   * @return key used for expanding the template in the editor
   */
  public final @NotNull @NlsSafe String getKey() {
    return myKey;
  }

  /**
   * @return template name displayed in UI
   */
  public @NotNull @NlsSafe String getPresentableName() {
    return myPresentableName;
  }

  /**
   * @return template description displayed in UI
   */
  public @NotNull @NlsContexts.DetailedDescription String getDescription() {
    return myLazyDescription.getValue();
  }

  /**
   * @return short example of the expanded form shown in the completion popup and templates tree on the configuration page
   */
  public @NotNull @NlsSafe String getExample() {
    return myExample;
  }

  public boolean startInWriteAction() {
    return true;
  }

  /**
   * @return {@code true} if general postfix templates setting is enabled and this template is enabled in settings
   */
  public boolean isEnabled(PostfixTemplateProvider provider) {
    final PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    return settings.isPostfixTemplatesEnabled() && settings.isTemplateEnabled(this, provider);
  }

  /**
   * Determines whether this template can be used in the given context specified by the parameters.
   *
   * @param context      PSI element before the template key
   * @param copyDocument copy of the document that contains changes introduced
   *                     in {@link PostfixTemplateProvider#preCheck(PsiFile, Editor, int)} method
   * @param newOffset    offset before the template key
   * @return {@code true} if template is applicable in the given context, {@code false} otherwise
   */
  public abstract boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset);

  /**
   * Inserts the template content in the given editor.
   *
   * @param context PSI element before the template key
   * @param editor  current editor
   */
  public abstract void expand(@NotNull PsiElement context, @NotNull Editor editor);

  /**
   * @return the {@link PostfixTemplateProvider} that provided this template
   */
  public @Nullable PostfixTemplateProvider getProvider() {
    return myProvider;
  }

  /**
   * Built-in templates cannot be removed.
   * If they are editable, they can be restored to the default state.
   */
  public boolean isBuiltin() {
    return true;
  }

  /**
   * Return true if this template can be edited.
   * <p>
   * Note that a template can be edited if its provider is not {@code null} and its key starts with {@code .} (dot), e.g., {@code .iter}.
   */
  public boolean isEditable() {
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PostfixTemplate template)) return false;
    return Objects.equals(myId, template.myId) &&
           Objects.equals(myPresentableName, template.myPresentableName) &&
           Objects.equals(myKey, template.myKey) &&
           Objects.equals(getDescription(), template.getDescription()) &&
           Objects.equals(myExample, template.myExample) &&
           Objects.equals(myProvider, template.myProvider);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myId, myPresentableName, myKey, getDescription(), myExample, myProvider);
  }
}
