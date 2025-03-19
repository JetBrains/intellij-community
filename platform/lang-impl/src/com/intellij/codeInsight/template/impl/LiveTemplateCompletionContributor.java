// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.CustomLiveTemplateBase;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.codeInsight.template.impl.ListTemplatesHandler.filterTemplatesByPrefix;

public final class LiveTemplateCompletionContributor extends CompletionContributor implements DumbAware {
  private static final Key<Boolean> ourShowTemplatesInTests = Key.create("ShowTemplatesInTests");

  @TestOnly
  public static void setShowTemplatesInTests(boolean show, @NotNull Disposable parentDisposable) {
    TestModeFlags.set(ourShowTemplatesInTests, show, parentDisposable);
  }

  public static boolean shouldShowAllTemplates() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return TestModeFlags.is(ourShowTemplatesInTests);
    }
    return Registry.is("show.live.templates.in.completion");
  }

  public LiveTemplateCompletionContributor() {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {
      @Override
      protected void addCompletions(final @NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        ProgressManager.checkCanceled();
        final PsiFile file = parameters.getPosition().getContainingFile();
        if (file instanceof PsiPlainTextFile && EditorTextField.managesEditor(parameters.getEditor())) {
          return;
        }

        PrefixMatcher matcher = result.getPrefixMatcher();
        if (matcher instanceof CamelHumpMatcher && ((CamelHumpMatcher)matcher).isTypoTolerant()) {
          // template matching uses editor content, not the supplied matcher
          // so if the first typo-intolerant invocation didn't produce results, this one won't, too
          return;
        }

        Editor editor = parameters.getEditor();
        int offset = editor.getCaretModel().getOffset();
        final List<TemplateImpl> availableTemplates = TemplateManagerImpl.listApplicableTemplates(
          TemplateActionContext.expanding(file, editor));
        final Map<TemplateImpl, String> templates = filterTemplatesByPrefix(availableTemplates, editor, offset, false, false);
        boolean isAutopopup = parameters.getInvocationCount() == 0;
        if (shouldShowAllTemplates()) {
          final AtomicBoolean templatesShown = new AtomicBoolean(false);
          final CompletionResultSet finalResult = result;
          boolean showLiveTemplatesOnTop = Registry.is("ide.completion.show.live.templates.on.top");
          if (showLiveTemplatesOnTop) {
            ensureTemplatesShown(templatesShown, templates, availableTemplates, finalResult, isAutopopup);
            showCustomLiveTemplates(parameters, result);
          }

          result.runRemainingContributors(parameters, completionResult -> {
            finalResult.passResult(completionResult);
            if (completionResult.isStartMatch()) {
              ensureTemplatesShown(templatesShown, templates, availableTemplates, finalResult, isAutopopup);
            }
          });

          ensureTemplatesShown(templatesShown, templates, availableTemplates, result, isAutopopup);
          if (!showLiveTemplatesOnTop) {
            showCustomLiveTemplates(parameters, result);
          }
          return;
        }

        if (!isAutopopup) return;

        // custom templates should handle this situation by itself (return true from hasCompletionItems() and provide lookup element)
        // regular templates won't be shown in this case
        if (!customTemplateAvailableAndHasCompletionItem(null, editor, file, offset)) {
          TemplateImpl template = findFullMatchedApplicableTemplate(editor, offset, availableTemplates);
          if (template != null) {
            result.withPrefixMatcher(template.getKey())
              .addElement(new LiveTemplateLookupElementImpl(template, true));
          }
        }

        for (Map.Entry<TemplateImpl, String> possible : templates.entrySet()) {
          ProgressManager.checkCanceled();
          String templateKey = possible.getKey().getKey();
          String currentPrefix = possible.getValue();
          result.withPrefixMatcher(currentPrefix)
            .restartCompletionOnPrefixChange(templateKey);
        }
      }
    });
  }

  public static boolean customTemplateAvailableAndHasCompletionItem(@Nullable Character shortcutChar, @NotNull Editor editor, @NotNull PsiFile file, int offset) {
    CustomTemplateCallback callback = new CustomTemplateCallback(editor, file);
    TemplateActionContext templateActionContext = TemplateActionContext.expanding(file, editor);
    for (CustomLiveTemplate customLiveTemplate : TemplateManagerImpl.listApplicableCustomTemplates(templateActionContext)) {
      ProgressManager.checkCanceled();
      if (customLiveTemplate instanceof CustomLiveTemplateBase) {
        if ((shortcutChar == null || customLiveTemplate.getShortcut() == shortcutChar.charValue())
            && ((CustomLiveTemplateBase)customLiveTemplate).hasCompletionItem(callback, offset)) {
          return customLiveTemplate.computeTemplateKey(callback) != null;
        }
      }
    }
    return false;
  }

  private static void ensureTemplatesShown(AtomicBoolean templatesShown,
                                           Map<TemplateImpl, String> templates,
                                           List<? extends TemplateImpl> availableTemplates,
                                           CompletionResultSet result,
                                           boolean isAutopopup) {
    if (!templatesShown.getAndSet(true)) {
      var templateKeys = ContainerUtil.map(availableTemplates, template -> template.getKey());

      result.restartCompletionOnPrefixChange(StandardPatterns.string().with(new PatternCondition<>("type after non-identifier") {
        @Override
        public boolean accepts(@NotNull String s, ProcessingContext context) {
          return s.length() > 1 &&
                 !Character.isJavaIdentifierPart(s.charAt(s.length() - 2)) &&
                 ContainerUtil.exists(templateKeys, template -> s.endsWith(template));
        }
      }));
      for (final Map.Entry<TemplateImpl, String> entry : templates.entrySet()) {
        ProgressManager.checkCanceled();
        if (isAutopopup && entry.getKey().getShortcutChar() == TemplateSettings.NONE_CHAR) continue;
        result.withPrefixMatcher(result.getPrefixMatcher().cloneWithPrefix(StringUtil.notNullize(entry.getValue())))
          .addElement(new LiveTemplateLookupElementImpl(entry.getKey(), false));
      }
    }
  }

  private static void showCustomLiveTemplates(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    TemplateActionContext templateActionContext = TemplateActionContext.expanding(
      parameters.getPosition().getContainingFile(), parameters.getEditor());
    for (CustomLiveTemplate customLiveTemplate : TemplateManagerImpl.listApplicableCustomTemplates(templateActionContext)) {
      ProgressManager.checkCanceled();
      if (customLiveTemplate instanceof CustomLiveTemplateBase) {
        ((CustomLiveTemplateBase)customLiveTemplate).addCompletions(parameters, result);
      }
    }
  }

  public static @Nullable TemplateImpl findFullMatchedApplicableTemplate(@NotNull Editor editor,
                                                                         int offset,
                                                                         @NotNull Collection<? extends TemplateImpl> availableTemplates) {
    Map<TemplateImpl, String> templates = filterTemplatesByPrefix(availableTemplates, editor, offset, true, false);
    if (templates.size() == 1) {
      TemplateImpl template = ContainerUtil.getFirstItem(templates.keySet());
      if (template != null) {
        return template;
      }
    }
    return null;
  }

}
