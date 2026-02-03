// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightBundle;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class LiveTemplateLookupActionProvider implements LookupActionProvider {
  @Override
  public void fillActions(@NotNull LookupElement element, final @NotNull Lookup lookup, @NotNull Consumer<? super @NotNull LookupElementAction> consumer) {
    if (element instanceof LiveTemplateLookupElementImpl) {
      final TemplateImpl template = ((LiveTemplateLookupElementImpl)element).getTemplate();
      final TemplateImpl templateFromSettings = TemplateSettings.getInstance().getTemplate(template.getKey(), template.getGroupName());

      if (templateFromSettings != null) {
        consumer.consume(new LookupElementAction(PlatformIcons.EDIT, CodeInsightBundle.message("action.text.edit.live.template.settings")) {
          @Override
          public Result performLookupAction() {
            final Project project = lookup.getProject();
            ApplicationManager.getApplication().invokeLater(() -> {
              if (project.isDisposed()) return;

              final LiveTemplatesConfigurable configurable = new LiveTemplatesConfigurable();
              ShowSettingsUtil.getInstance().editConfigurable(project, configurable, () -> configurable.getTemplateListPanel().editTemplate(template));
            });
            return Result.HIDE_LOOKUP;
          }
        });


        consumer.consume(new LookupElementAction(AllIcons.Actions.Cancel, CodeInsightBundle.message("action.text.disable.live.template", template.getKey())) {
          @Override
          public Result performLookupAction() {
            ApplicationManager.getApplication().invokeLater(() -> templateFromSettings.setDeactivated(true));
            return Result.HIDE_LOOKUP;
          }
        });
      }
    }
  }
}
