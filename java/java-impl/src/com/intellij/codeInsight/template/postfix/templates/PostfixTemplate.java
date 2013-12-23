/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PostfixTemplate {
  @NotNull private final String myPresentableName;
  @NotNull private final String myKey;
  @NotNull private final String myDescription;
  @NotNull private final String myExample;

  @NotNull
  public static final ExtensionPointName<PostfixTemplate> EP_NAME = ExtensionPointName.create("com.intellij.postfixTemplate");

  protected PostfixTemplate(@NotNull String name, @NotNull String description, @NotNull String example) {
    this(name, "." + name, description, example);
  }

  protected PostfixTemplate(@NotNull String name, @NotNull String key, @NotNull String description, @NotNull String example) {
    myPresentableName = name;
    myKey = key;
    myDescription = description;
    myExample = example;
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

  public boolean isEnabled() {
    final PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    return settings != null && settings.isPostfixTemplatesEnabled() && settings.isTemplateEnabled(this);
  }

  @Nullable
  public static PsiExpression getTopmostExpression(PsiElement context) {
    return PsiTreeUtil.getTopmostParentOfType(context, PsiExpression.class);
  }

  public abstract boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset);

  public abstract void expand(@NotNull PsiElement context, @NotNull Editor editor);
}
