package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupActionProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;

/**
 * @author peter
 */
public class LiveTemplateLookupActionProvider implements LookupActionProvider {
  @Override
  public void fillActions(LookupElement element, final Lookup lookup, Consumer<LookupElementAction> consumer) {
    if (element instanceof LiveTemplateLookupElement) {
      final TemplateImpl template = ((LiveTemplateLookupElement)element).getTemplate();

      consumer.consume(new LookupElementAction(PlatformIcons.EDIT, "Edit live template settings") {
        @Override
        public Result performLookupAction() {
          final Project project = lookup.getEditor().getProject();
          assert project != null;
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              if (project.isDisposed()) return;

              final LiveTemplatesConfigurable configurable = new LiveTemplatesConfigurable();
              ShowSettingsUtil.getInstance().editConfigurable(project, configurable, new Runnable() {
                @Override
                public void run() {
                  configurable.getTemplateListPanel().editTemplate(template);
                }
              });
            }
          });
          return Result.HIDE_LOOKUP;
        }
      });

      final TemplateImpl templateFromSettings = TemplateSettings.getInstance().getTemplate(template.getKey(), template.getGroupName());
      if (templateFromSettings != null) {
        consumer.consume(new LookupElementAction(AllIcons.Actions.Delete, String.format("Disable '%s' template", template.getKey())) {
          @Override
          public Result performLookupAction() {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                templateFromSettings.setDeactivated(true);
              }
            });
            return Result.HIDE_LOOKUP;
          }
        });
      }
    }
  }
}
