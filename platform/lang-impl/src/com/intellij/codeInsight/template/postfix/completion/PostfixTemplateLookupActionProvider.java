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
package com.intellij.codeInsight.template.postfix.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupActionProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesCheckboxTree;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesConfigurable;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatesUtils;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;

public class PostfixTemplateLookupActionProvider implements LookupActionProvider {
  @Override
  public void fillActions(LookupElement element, final Lookup lookup, Consumer<LookupElementAction> consumer) {
    if (element instanceof PostfixTemplateLookupElement) {
      final PostfixTemplateLookupElement templateLookupElement = (PostfixTemplateLookupElement)element;
      final PostfixTemplate template = templateLookupElement.getPostfixTemplate();

      consumer.consume(new LookupElementAction(PlatformIcons.EDIT, "Edit postfix templates settings") {
        @Override
        public Result performLookupAction() {
          final Project project = lookup.getProject();
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              if (project.isDisposed()) return;

              final PostfixTemplatesConfigurable configurable = new PostfixTemplatesConfigurable();
              ShowSettingsUtil.getInstance().editConfigurable(project, configurable, new Runnable() {
                @Override
                public void run() {
                  PostfixTemplatesCheckboxTree templatesTree = configurable.getTemplatesTree();
                  if (templatesTree != null) {
                    templatesTree.selectTemplate(template, PostfixTemplatesUtils
                      .getLangForProvider(templateLookupElement.getProvider()));
                  }
                }
              });
            }
          });
          return Result.HIDE_LOOKUP;
        }
      });

      final PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
      if (settings != null && settings.isTemplateEnabled(template, templateLookupElement.getProvider())) {
        consumer.consume(new LookupElementAction(AllIcons.Actions.Delete, String.format("Disable '%s' template", template.getKey())) {
          @Override
          public Result performLookupAction() {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                settings.disableTemplate(template, templateLookupElement.getProvider());
              }
            });
            return Result.HIDE_LOOKUP;
          }
        });
      }
    }
  }
}
