// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateMetaData;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixEditableTemplateProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public abstract class PostfixTemplate {
  @NotNull private final String myPresentableName;
  @NotNull private final String myKey;
  @NotNull private final String myDescription;
  @NotNull private final String myExample;
  @Nullable private final PostfixTemplateProvider myProvider;

  /**
   * @deprecated use {@link #PostfixTemplate(String, String, PostfixTemplateProvider)}
   */
  protected PostfixTemplate(@NotNull String name, @NotNull String example) {
    this(name, "." + name, example, null);
  }
  
  protected PostfixTemplate(@NotNull String name, @NotNull String example, @Nullable PostfixTemplateProvider provider) {
    this(name, "." + name, example, provider);
  }
  
  /**
   * @deprecated use {@link #PostfixTemplate(String, String, String, PostfixTemplateProvider)} 
   */
  protected PostfixTemplate(@NotNull String name, @NotNull String key, @NotNull String example) {
    this(name, key, example, null);
  }

  protected PostfixTemplate(@NotNull String name,
                            @NotNull String key,
                            @NotNull String example,
                            @Nullable PostfixTemplateProvider provider) {
    String tempDescription;
    myPresentableName = name;
    myKey = key;
    myExample = example;

    try {
      tempDescription = new PostfixTemplateMetaData(this).getDescription().getText();
    }
    catch (IOException e) {
      tempDescription = "Under construction";
    }
    myDescription = tempDescription;
    myProvider = provider;
  }

  @NotNull
  public final String getKey() {
    return myKey;
  }

  @NotNull
  public String getPresentableName() {
    return myPresentableName;
  }

  @NotNull
  public String getDescription() {
    return myDescription;
  }

  @NotNull
  public String getExample() {
    return myExample;
  }

  public boolean startInWriteAction() {
    return true;
  }

  public boolean isEnabled(PostfixTemplateProvider provider) {
    final PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    return settings != null && settings.isPostfixTemplatesEnabled() && settings.isTemplateEnabled(this, provider);
  }

  public abstract boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset);

  public abstract void expand(@NotNull PsiElement context, @NotNull Editor editor);

  @Nullable
  public PostfixTemplateProvider getProvider() {
    return myProvider;
  }

  public boolean isBuiltin() {
    return true;
  }

  public boolean isEditable() {
    return getProvider() instanceof PostfixEditableTemplateProvider;
  }
}
