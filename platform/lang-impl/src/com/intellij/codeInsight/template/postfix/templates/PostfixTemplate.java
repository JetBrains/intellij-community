/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateMetaData;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class PostfixTemplate {
  @NotNull private final String myPresentableName;
  @NotNull private final String myKey;
  @NotNull private final String myDescription;
  @NotNull private final String myExample;

  protected PostfixTemplate(@NotNull String name, @NotNull String example) {
    this(name, "." + name, example);
  }

  protected PostfixTemplate(@NotNull String name, @NotNull String key, @NotNull String example) {
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

  public boolean isEnabled(PostfixTemplateProvider provider) {
    final PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    return settings != null && settings.isPostfixTemplatesEnabled() && settings.isTemplateEnabled(this, provider);
  }

  public abstract boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset);

  public abstract void expand(@NotNull PsiElement context, @NotNull Editor editor);
}
