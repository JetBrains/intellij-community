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

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class LanguagePostfixTemplate extends LanguageExtension<PostfixTemplateProvider> {
  public static final LanguagePostfixTemplate LANG_EP = new LanguagePostfixTemplate();
  public static final String EP_NAME = "com.intellij.codeInsight.template.postfixTemplateProvider";

  private LanguagePostfixTemplate() {
    super(EP_NAME);
  }


  @NotNull
  @Override
  protected List<PostfixTemplateProvider> buildExtensions(@NotNull String stringKey, @NotNull Language key) {
    List<PostfixTemplateProvider> providers = super.buildExtensions(stringKey, key);
    validateTemplatesForLanguage(key, providers);
    return providers;
  }

  private static void validateTemplatesForLanguage(Language key, List<PostfixTemplateProvider> providers) {
    Set<String> templateKeys = ContainerUtil.newHashSet();
    for (PostfixTemplateProvider provider : providers) {
      for (PostfixTemplate template : provider.getTemplates()) {
        if (!templateKeys.add(template.getKey())) {
          throw new IllegalStateException("Duplicated key " + template.getKey() + " for language " + key.getID());
        }
      }
    }
  }
}
