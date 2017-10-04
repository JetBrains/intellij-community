// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates.editable;

import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateStorage;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public interface PostfixEditableTemplateProvider extends PostfixTemplateProvider {
  @NotNull
  @Override
  default Set<PostfixTemplate> getTemplates() {
    Map<String, PostfixTemplate> map = new HashMap<>();
    for (PostfixTemplate builtinTemplate : getBuiltinTemplates()) {
      map.put(builtinTemplate.getKey(), builtinTemplate);
    }
    for (PostfixTemplate template : PostfixTemplateStorage.getInstance().getTemplates(this)) {
      map.put(template.getKey(), template);
    }
    return ContainerUtil.newHashSet(map.values());
  }

  @NotNull
  default Set<? extends PostfixTemplate> getBuiltinTemplates() {
    return Collections.emptySet();
  }

  @NotNull
  String getId();

  @NotNull
  String getName();

  @Nullable
  PostfixTemplateEditor createEditor();

  @NotNull
  PostfixTemplate readExternal(@NotNull String key, @NotNull Element template);

  void writeExternal(@NotNull PostfixTemplate template, @NotNull Element parentElement);
}