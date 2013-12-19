package com.intellij.codeInsight.template.postfix.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupActionProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.codeInsight.template.postfix.settings.PostfixCompletionConfigurable;
import com.intellij.codeInsight.template.postfix.settings.PostfixCompletionSettings;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesListPanel;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
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
      final PostfixTemplate template = ((PostfixTemplateLookupElement)element).getPostfixTemplate();

      consumer.consume(new LookupElementAction(PlatformIcons.EDIT, "Edit postfix completion settings") {
        @Override
        public Result performLookupAction() {
          final Project project = lookup.getEditor().getProject();
          assert project != null;
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              if (project.isDisposed()) return;

              final PostfixCompletionConfigurable configurable = new PostfixCompletionConfigurable();
              ShowSettingsUtil.getInstance().editConfigurable(project, configurable, new Runnable() {
                @Override
                public void run() {
                  PostfixTemplatesListPanel templatesListPanel = configurable.getTemplatesListPanel();
                  if (templatesListPanel != null) {
                    templatesListPanel.selectTemplate(template);
                  }
                }
              });
            }
          });
          return Result.HIDE_LOOKUP;
        }
      });

      final PostfixCompletionSettings settings = PostfixCompletionSettings.getInstance();
      if (settings != null && settings.isTemplateEnabled(template)) {
        consumer.consume(new LookupElementAction(AllIcons.Actions.Delete, String.format("Disable '%s' template", template.getKey())) {
          @Override
          public Result performLookupAction() {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                settings.disableTemplate(template);
              }
            });
            return Result.HIDE_LOOKUP;
          }
        });
      }
    }
  }
}
