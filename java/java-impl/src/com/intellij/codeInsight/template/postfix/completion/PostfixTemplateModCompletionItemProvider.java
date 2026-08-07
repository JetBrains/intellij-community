// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.completion;

import com.intellij.codeInsight.completion.CompletionSorter;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementCustomPreviewHolder;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings;
import com.intellij.codeInsight.template.postfix.templates.LanguagePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixModExpander;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatesUtils;
import com.intellij.icons.AllIcons;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.ModCompletionItemProvider;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Set;

/**
 * Provides postfix templates as {@link ModCompletionItem}s for use in ModCompletion.
 * Discovers applicable postfix templates at the current offset and emits a completion item
 * for each template that supports {@link PostfixTemplate#createModExpander()}.
 */
public final class PostfixTemplateModCompletionItemProvider implements ModCompletionItemProvider {

  @Override
  public void provideItems(@NotNull CompletionContext context, @NotNull ModCompletionResult sink) {
    PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    if (!settings.isPostfixTemplatesEnabled()) return;

    PsiFile file = context.originalFile();
    int offset = context.offset();
    if (offset <= 0) return;

    var language = PsiUtilCore.getLanguageAtOffset(file, offset);

    for (PostfixTemplateProvider provider : LanguagePostfixTemplate.LANG_EP.allForLanguage(language)) {
      ProgressManager.checkCanceled();
      CharSequence fileContent = file.getFileDocument().getCharsSequence();
      String key = PostfixLiveTemplate.computeTemplateKeyWithoutContextChecking(provider, context.getProject(), language, fileContent, offset);
      if (key == null) continue;

      int newOffset = offset - key.length();
      StringBuilder contentWithoutKey = new StringBuilder();
      contentWithoutKey.append(fileContent.subSequence(0, newOffset));
      contentWithoutKey.append(fileContent.subSequence(offset, fileContent.length()));
      PsiFile copyFile = PostfixLiveTemplate.copyFile(file, contentWithoutKey);
      provider.prepareCopyForModCommand(copyFile, newOffset);

      Document copyDocument = copyFile.getFileDocument();
      PsiElement copyContext = CustomTemplateCallback.getContext(copyFile, newOffset > 0 ? newOffset - 1 : newOffset);

      for (PostfixTemplate template : PostfixTemplatesUtils.getAvailableTemplates(provider)) {
        ProgressManager.checkCanceled();
        if (!isDumbEnough(template, copyContext)) continue;
        //doesn't support `..`
        //so no mod command postfix for `..`
        if (!template.getKey().startsWith(key)) continue;
        if (!template.isEnabled(provider)) continue;
        if (!template.isApplicable(copyContext, copyDocument, newOffset)) continue;
        if (!template.isApplicableForModCommand()) continue;
        if (template.createModExpander() == null) continue;
        sink.accept(new PostfixModCompletionItem(provider, template, key));
      }
    }
  }

  private static boolean isDumbEnough(@NotNull PostfixTemplate template, @NotNull PsiElement context) {
    DumbService dumbService = DumbService.getInstance(context.getProject());
    return dumbService.isUsableInCurrentContext(template);
  }

  @VisibleForTesting
  public static final class PostfixModCompletionItem implements ModCompletionItem, LookupElementCustomPreviewHolder {
    private final @NotNull PostfixTemplate myTemplate;
    private final @NotNull PostfixTemplateProvider myProvider;
    private final @NotNull String myKey;

    PostfixModCompletionItem(@NotNull PostfixTemplateProvider provider,
                                     @NotNull PostfixTemplate template,
                                     @NotNull String key) {
      myTemplate = template;
      myProvider = provider;
      myKey = key;
    }

    @Override
    public @NotNull String mainLookupString() {
      return StringUtil.trimStart(myTemplate.getKey(), ".");
    }

    @Override
    public @NotNull AutoCompletionPolicy autoCompletionPolicy() {
      return AutoCompletionPolicy.NEVER_AUTOCOMPLETE;
    }

    @Override
    public @NotNull Set<String> additionalLookupStrings() {
      return Set.of();
    }

    @Override
    public @NotNull Object contextObject() {
      return myTemplate;
    }

    @Override
    public @NotNull ModCompletionItemPresentation presentation() {
      return new ModCompletionItemPresentation(MarkupText.plainText(myTemplate.getPresentableName()))
        .withMainIcon(() -> AllIcons.Nodes.Template)
        .withDetailText(MarkupText.plainText(myTemplate.getExample()));
    }

    /**
     * The completion engine builds the context from the top-level file, so switch to the injected fragment
     * as a whole via {@link ActionContext#mapToInjected()}: the key range and the expansion must be computed
     * in the same coordinate space as {@link ActionContext#offset()} and {@link ActionContext#selection()}.
     */
    private @NotNull ModCommand expand(@NotNull ActionContext ctx, @NotNull String key) {
      PostfixModExpander expander = myTemplate.createModExpander();
      if (expander == null) return ModCommand.nop();
      ActionContext context = ctx.mapToInjected();
      TextRange keyRange = PostfixTemplatesUtils.computeKeyRange(context, key, myTemplate.getKey());
      return expander.expand(context, myProvider, keyRange);
    }

    @Override
    public @NotNull ModCommand perform(@NotNull ActionContext actionContext, @NotNull InsertionContext insertionContext) {
      return expand(actionContext, myKey);
    }

    @Override
    public @NotNull IntentionPreviewInfo preview(@NotNull ActionContext ctx) {
      if (!myTemplate.isApplicableForModCommand()) return IntentionPreviewInfo.EMPTY;
      PsiFile file = ctx.file();
      CharSequence sequence = file.getFileDocument().getCharsSequence();
      String key = PostfixLiveTemplate.computeTemplateKeyWithoutContextChecking(
        myProvider, file.getProject(), file.getLanguage(), sequence, ctx.offset());
      if (key == null) return IntentionPreviewInfo.EMPTY;
      return IntentionPreviewUtils.getModCommandPreview(expand(ctx, key), ctx);
    }
  }

  @Override
  public boolean isEnabled() {
    return Registry.is("postfix.template.mod.completion.enabled", false);
  }

  @Override
  public @NotNull CompletionSorter getSorter(@NotNull CompletionContext context) {
    CompletionSorter sorter = CompletionSorter.defaultSorter(context, context.matcher());
    return sorter.weigh(new LookupElementWeigher("postfix_length") {
      @Override
      public @NotNull Integer weigh(@NotNull LookupElement element) {
        return element.getLookupString().length();
      }
    });
  }
}
