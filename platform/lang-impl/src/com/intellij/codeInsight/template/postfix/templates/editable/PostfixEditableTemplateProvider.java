// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates.editable;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface PostfixEditableTemplateProvider<T extends PostfixTemplate> extends PostfixTemplateProvider {
  @NotNull
  default String getId() {
    return getClass().getName();
  }

  @Nullable
  default String getPresentableName() {
    return null;
  }

  @Nullable
  default PostfixTemplateEditor<T> createEditor(@Nullable Project project) {
    return null;
  }

  @Nullable
  default T readExternalTemplate(@NotNull String id, @NotNull String name, @NotNull Element template) {
    return null;
  }

  default void writeExternalTemplate(@NotNull PostfixTemplate template, @NotNull Element parentElement) {
  }
}