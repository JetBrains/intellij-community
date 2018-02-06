// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.codeInsight.template.postfix.templates.LanguagePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixEditableTemplateProvider;
import com.intellij.lang.Language;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@State(name = "PostfixTemplates", storages = @Storage("postfixTemplates.xml"))
public class PostfixTemplateStorage implements PersistentStateComponent<Element> {
  private static final String TEMPLATE_TAG = "template";
  private static final String PROVIDER_ATTR_NAME = "provider";
  private static final String KEY_ATTR_NAME = "key";

  private final Map<String, PostfixEditableTemplateProvider> myEditableProviders;
  private final MultiMap<String, PostfixTemplate> myTemplates = MultiMap.createSmart();
  private final List<Element> myUnloadedTemplates = ContainerUtil.newSmartList();

  public PostfixTemplateStorage() {
    myEditableProviders = new HashMap<>();
    for (PostfixTemplateProvider provider : LanguagePostfixTemplate.LANG_EP.allForLanguage(Language.ANY)) {
      if (provider instanceof PostfixEditableTemplateProvider) {
        myEditableProviders.put(((PostfixEditableTemplateProvider)provider).getId(), (PostfixEditableTemplateProvider)provider);
      }
    }
  }

  @NotNull
  public static PostfixTemplateStorage getInstance() {
    return ServiceManager.getService(PostfixTemplateStorage.class);
  }

  @NotNull
  public Set<PostfixTemplate> getTemplates(@NotNull PostfixEditableTemplateProvider provider) {
    return new HashSet<>(myTemplates.get(provider.getId()));
  }

  public void setTemplates(@NotNull PostfixEditableTemplateProvider provider, @NotNull Collection<PostfixTemplate> templates) {
    myTemplates.put(provider.getId(), templates);
  }

  @Override
  public void loadState(@NotNull final Element state) {
    myTemplates.clear();
    List<Element> templatesElement = state.getChildren(TEMPLATE_TAG);
    for (Element templateElement : templatesElement) {
      PostfixEditableTemplateProvider provider = myEditableProviders.get(templateElement.getAttributeValue(PROVIDER_ATTR_NAME, ""));
      if (provider != null) {
        String templateKey = templateElement.getAttributeValue(KEY_ATTR_NAME, "");
        myTemplates.putValue(provider.getId(), provider.readExternal(templateKey, templateElement));
      }
      else {
        myUnloadedTemplates.add(templateElement);
      }
    }
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    element.addContent(myUnloadedTemplates);
    for (Map.Entry<String, Collection<PostfixTemplate>> entry : myTemplates.entrySet()) {
      for (PostfixTemplate template : entry.getValue()) {
        PostfixTemplateProvider provider = template.getProvider();
        if (provider instanceof PostfixEditableTemplateProvider) {
          element.addContent(writeTemplate(template, (PostfixEditableTemplateProvider)provider));
        }
      }
    }
    return element;
  }

  private static Element writeTemplate(@NotNull PostfixTemplate template, @NotNull PostfixEditableTemplateProvider provider) {
    Element templateElement = new Element(TEMPLATE_TAG);
    templateElement.setAttribute(PROVIDER_ATTR_NAME, provider.getId());
    templateElement.setAttribute(KEY_ATTR_NAME, template.getKey());
    provider.writeExternal(template, templateElement);
    return templateElement;
  }
}