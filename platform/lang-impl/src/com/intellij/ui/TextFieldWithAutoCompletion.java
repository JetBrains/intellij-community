/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.textCompletion.TextCompletionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * <p/>
 * It is text field with autocompletion from list of values.
 * <p/>
 * Autocompletion is implemented via {@code TextFieldWithAutoCompletionContributor}.
 * Use {@code setVariants} set list of values for autocompletion.
 *
 * @author Roman Chernyatchik
 */
public class TextFieldWithAutoCompletion<T> extends LanguageTextField {

  public static final TextFieldWithAutoCompletionListProvider EMPTY_COMPLETION = new StringsCompletionProvider(null, null);
  private final boolean myShowAutocompletionIsAvailableHint;
  private final TextFieldWithAutoCompletionListProvider<T> myProvider;

  @SuppressWarnings("unchecked")
  public TextFieldWithAutoCompletion() {
    this(null, EMPTY_COMPLETION, false, null);
  }


  public TextFieldWithAutoCompletion(final Project project,
                                     @NotNull final TextFieldWithAutoCompletionListProvider<T> provider,
                                     final boolean showAutocompletionIsAvailableHint, @Nullable final String text) {
    super(project == null ? null : PlainTextLanguage.INSTANCE, project, text == null ? "" : text);

    myShowAutocompletionIsAvailableHint = showAutocompletionIsAvailableHint;
    myProvider = provider;

    installCompletion(getDocument(), project, provider, true);
  }

  public static TextFieldWithAutoCompletion<String> create(final Project project,
                                                           @NotNull final Collection<String> items,
                                                           final boolean showAutocompletionIsAvailableHint,
                                                           @Nullable final String text) {
    return create(project, items, null, showAutocompletionIsAvailableHint, text);
  }

  public static TextFieldWithAutoCompletion<String> create(final Project project,
                                                           @NotNull final Collection<String> items,
                                                           @Nullable final Icon icon,
                                                           final boolean showAutocompletionIsAvailableHint,
                                                           @Nullable final String text) {
    return new TextFieldWithAutoCompletion<String>(project, new StringsCompletionProvider(items, icon), showAutocompletionIsAvailableHint,
                                                   text);
  }

  public void setVariants(@NotNull Collection<T> variants) {
    myProvider.setItems(variants);
  }

  public <T> void installProvider(@NotNull TextFieldWithAutoCompletionListProvider<T> provider) {
    installCompletion(getDocument(), getProject(), provider, true);
  }

  public static void installCompletion(@NotNull Document document,
                                       @NotNull Project project,
                                       @NotNull TextFieldWithAutoCompletionListProvider provider,
                                       boolean autoPopup) {
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile != null) {
      TextCompletionUtil.installProvider(psiFile, provider, autoPopup);
    }
  }

  @Override
  protected EditorEx createEditor() {
    EditorEx editor = super.createEditor();

    if (myShowAutocompletionIsAvailableHint) {
      TextCompletionUtil.installCompletionHint(editor);
    }

    return editor;
  }

  public static class StringsCompletionProvider extends TextFieldWithAutoCompletionListProvider<String> implements DumbAware {
    @Nullable private final Icon myIcon;

    public StringsCompletionProvider(@Nullable final Collection<String> variants,
                                     @Nullable final Icon icon) {
      super(variants);
      myIcon = icon;
    }

    @Override
    public int compare(final String item1, final String item2) {
      return StringUtil.compare(item1, item2, false);
    }

    @Override
    protected Icon getIcon(@NotNull final String item) {
      return myIcon;
    }

    @NotNull
    @Override
    protected String getLookupString(@NotNull final String item) {
      return item;
    }

    @Override
    protected String getTailText(@NotNull final String item) {
      return null;
    }

    @Override
    protected String getTypeText(@NotNull final String item) {
      return null;
    }
  }
}
