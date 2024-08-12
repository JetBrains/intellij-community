// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import com.intellij.util.textCompletion.TextCompletionProvider;
import com.intellij.util.textCompletion.TextCompletionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TextFieldCompletionProvider implements TextCompletionProvider, PossiblyDumbAware {
  protected boolean myCaseInsensitivity;

  protected TextFieldCompletionProvider() {
    this(false);
  }

  protected TextFieldCompletionProvider(boolean caseInsensitivity) {
    myCaseInsensitivity = caseInsensitivity;
  }

  @Override
  public @Nullable String getPrefix(@NotNull String text, int offset) {
    return getPrefix(text.substring(0, offset));
  }

  @Override
  public @NotNull CompletionResultSet applyPrefixMatcher(@NotNull CompletionResultSet result, @NotNull String prefix) {
    CompletionResultSet activeResult = result;

    if (!activeResult.getPrefixMatcher().getPrefix().equals(prefix)) {
      activeResult = activeResult.withPrefixMatcher(prefix);
    }

    if (isCaseInsensitivity()) {
      activeResult = activeResult.caseInsensitive();
    }

    return activeResult;
  }

  @Override
  public @Nullable CharFilter.Result acceptChar(char c) {
    return null;
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                     @NotNull String prefix,
                                     @NotNull CompletionResultSet result) {
    addCompletionVariants(parameters.getOriginalFile().getText(), parameters.getOffset(), prefix, result);
    result.stopHere();
  }

  public void apply(@NotNull EditorTextField field, @NotNull String text) {
    Project project = field.getProject();
    if (project != null) {
      field.setDocument(createDocument(project, text));
    }
  }

  public void apply(@NotNull EditorTextField field) {
    apply(field, "");
  }

  private Document createDocument(Project project, @NotNull String text) {
    return LanguageTextField.createDocument(text, PlainTextLanguage.INSTANCE, project, new TextCompletionUtil.DocumentWithCompletionCreator(this, true));
  }

  public boolean isCaseInsensitivity() {
    return myCaseInsensitivity;
  }

  protected @NotNull String getPrefix(@NotNull String currentTextPrefix) {
    return currentTextPrefix;
  }

  @Override
  public @Nullable String getAdvertisement() {
    return null;
  }

  protected abstract void addCompletionVariants(@NotNull String text,
                                                int offset,
                                                @NotNull String prefix,
                                                @NotNull CompletionResultSet result);

  public @NotNull EditorTextField createEditor(Project project) {
    return createEditor(project, true, null);
  }

  public @NotNull EditorTextField createEditor(Project project,
                                               final boolean shouldHaveBorder,
                                               final @Nullable Consumer<? super Editor> editorConstructionCallback) {
    return new EditorTextField(createDocument(project, ""), project, PlainTextLanguage.INSTANCE.getAssociatedFileType()) {
      @Override
      protected boolean shouldHaveBorder() {
        return shouldHaveBorder;
      }

      @Override
      protected void updateBorder(@NotNull EditorEx editor) {
        if (shouldHaveBorder) {
          super.updateBorder(editor);
        }
        else {
          editor.setBorder(null);
        }
      }

      @Override
      protected @NotNull EditorEx createEditor() {
        EditorEx result = super.createEditor();
        if (editorConstructionCallback != null) {
          editorConstructionCallback.consume(result);
        }
        return result;
      }
    };
  }
}
