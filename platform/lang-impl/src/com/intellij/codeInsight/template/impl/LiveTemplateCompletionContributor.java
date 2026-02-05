// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.CustomLiveTemplateBase;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings;
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
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
    return !ApplicationManager.getApplication().isUnitTestMode() || TestModeFlags.is(ourShowTemplatesInTests);
  }

  public LiveTemplateCompletionContributor() {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {
      @Override
      protected void addCompletions(final @NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        if (!shouldShowAllTemplates()) {
          return;
        }

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
        List<TemplateImpl> availableTemplates = TemplateManagerImpl.listApplicableTemplates(TemplateActionContext.expanding(file, editor));
        Map<TemplateImpl, String> templates = filterTemplatesByPrefix(availableTemplates, editor, offset, false, false);
        boolean isAutopopup = parameters.getInvocationCount() == 0;

        AtomicBoolean templatesShown = new AtomicBoolean(false);
        boolean showLiveTemplatesOnTop = Registry.is("ide.completion.show.live.templates.on.top");
        if (showLiveTemplatesOnTop) {
          ensureTemplatesShown(templatesShown, templates, availableTemplates, result, isAutopopup);
          showCustomLiveTemplates(parameters, result);
        }

        result.runRemainingContributors(parameters, completionResult -> {
          result.passResult(completionResult);
          if (completionResult.isStartMatch()) {
            ensureTemplatesShown(templatesShown, templates, availableTemplates, result, isAutopopup);
          }
        });

        ensureTemplatesShown(templatesShown, templates, availableTemplates, result, isAutopopup);
        if (!showLiveTemplatesOnTop) {
          showCustomLiveTemplates(parameters, result);
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
      if (!availableTemplates.isEmpty()) {
        var templateKeys = ContainerUtil.map(availableTemplates, template -> template.getKey());
        result.restartCompletionOnPrefixChange(StandardPatterns.string().afterNonJavaIdentifierPart().endsWithOneOf(templateKeys));
      }
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
    boolean showPostfixTemplateAsSeparateGroup =
      PostfixTemplatesSettings.getInstance().isShowAsSeparateGroup();
    for (CustomLiveTemplate customLiveTemplate : TemplateManagerImpl.listApplicableCustomTemplates(templateActionContext)) {
      ProgressManager.checkCanceled();
      if (showPostfixTemplateAsSeparateGroup && customLiveTemplate instanceof PostfixLiveTemplate) continue;
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
