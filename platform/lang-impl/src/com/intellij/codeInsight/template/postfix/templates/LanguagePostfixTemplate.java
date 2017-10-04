// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class LanguagePostfixTemplate extends LanguageExtension<PostfixTemplateProvider> {
  private static final Logger LOG = Logger.getInstance(LanguagePostfixTemplate.class);

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
    Map<String, PostfixTemplateProvider> templateKeys = ContainerUtil.newHashMap();
    for (PostfixTemplateProvider provider : providers) {
      for (PostfixTemplate template : provider.getTemplates()) {
        PostfixTemplateProvider oldProvider = templateKeys.put(template.getKey(), provider);
        if (oldProvider != null) {
          String oldPlugin = getPluginId(oldProvider);
          String newPlugin = getPluginId(provider);
          LOG.error("Duplicated postfix completion key '" + template.getKey() + "' for language " + key.getID() +
                    ". Possible you need to disable one of the plugins: " + oldPlugin + ", " + newPlugin);
        }
      }
    }
  }

  @NotNull
  private static String getPluginId(@NotNull PostfixTemplateProvider provider) {
    ClassLoader loader = provider.getClass().getClassLoader();
    if (loader instanceof PluginClassLoader) {
      return ((PluginClassLoader)loader).getPluginId().getIdString();
    }

    return "";
  }

}
