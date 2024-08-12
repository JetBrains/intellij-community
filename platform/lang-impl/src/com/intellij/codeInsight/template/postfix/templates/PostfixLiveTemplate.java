// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.completion.OffsetTranslator;
import com.intellij.codeInsight.template.CustomLiveTemplateBase;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.impl.CustomLiveTemplateLookupElement;
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateLookupElement;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings;
import com.intellij.diagnostic.CoreAttachmentFactory;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.templateLanguages.TemplateLanguageUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class PostfixLiveTemplate extends CustomLiveTemplateBase {
  public static final @NonNls String POSTFIX_TEMPLATE_ID = "POSTFIX_TEMPLATE_ID";
  private static final Logger LOG = Logger.getInstance(PostfixLiveTemplate.class);

  public @NotNull Set<@NlsSafe String> getAllTemplateKeys(PsiFile file, int offset) {
    Set<String> keys = new HashSet<>();
    Language language = PsiUtilCore.getLanguageAtOffset(file, offset);
    for (PostfixTemplateProvider provider : LanguagePostfixTemplate.LANG_EP.allForLanguage(language)) {
      ProgressManager.checkCanceled();
      keys.addAll(getKeys(provider));
    }
    return keys;
  }

  private static @Nullable @NlsSafe String computeTemplateKeyWithoutContextChecking(@NotNull PostfixTemplateProvider provider,
                                                                                    @NotNull CharSequence documentContent,
                                                                                    int currentOffset) {
    int startOffset = currentOffset;
    if (documentContent.length() < startOffset) {
      return null;
    }

    while (startOffset > 0) {
      ProgressManager.checkCanceled();
      char currentChar = documentContent.charAt(startOffset - 1);
      if (!Character.isJavaIdentifierPart(currentChar)) {
        if (!provider.isTerminalSymbol(currentChar)) {
          return null;
        }
        startOffset--;
        break;
      }
      startOffset--;
    }
    return String.valueOf(documentContent.subSequence(startOffset, currentOffset));
  }

  @Override
  public @Nullable @NlsSafe String computeTemplateKey(@NotNull CustomTemplateCallback callback) {
    Editor editor = callback.getEditor();
    CharSequence charsSequence = editor.getDocument().getCharsSequence();
    int offset = editor.getCaretModel().getOffset();
    for (PostfixTemplateProvider provider : LanguagePostfixTemplate.LANG_EP.allForLanguage(getLanguage(callback))) {
      String key = computeTemplateKeyWithoutContextChecking(provider, charsSequence, offset);
      if (key != null && isApplicableTemplate(provider, key, callback.getFile(), editor)) {
        return key;
      }
    }
    return null;
  }

  @Override
  public @Nullable @NlsSafe String computeTemplateKeyWithoutContextChecking(@NotNull CustomTemplateCallback callback) {
    Editor editor = callback.getEditor();
    int currentOffset = editor.getCaretModel().getOffset();
    for (PostfixTemplateProvider provider : LanguagePostfixTemplate.LANG_EP.allForLanguage(getLanguage(callback))) {
      ProgressManager.checkCanceled();
      String key = computeTemplateKeyWithoutContextChecking(provider, editor.getDocument().getCharsSequence(), currentOffset);
      if (key != null) return key;
    }
    return null;
  }

  @Override
  public boolean supportsMultiCaret() {
    return false;
  }

  @Override
  public void expand(final @NotNull String key, final @NotNull CustomTemplateCallback callback) {
    ThreadingAssertions.assertEventDispatchThread();

    Editor editor = callback.getEditor();
    PsiFile file = callback.getContext().getContainingFile();
    for (PostfixTemplateProvider provider : LanguagePostfixTemplate.LANG_EP.allForLanguage(getLanguage(callback))) {
      PostfixTemplate postfixTemplate = findApplicableTemplate(provider, key, editor, file);
      if (postfixTemplate != null) {
        expandTemplate(key, callback, editor, provider, postfixTemplate);
        return;
      }
    }

    // don't care about errors in multiCaret mode
    if (editor.getCaretModel().getAllCarets().size() == 1) {
      LOG.error("Template not found by key: " + key + "; offset = " + callback.getOffset(),
                CoreAttachmentFactory.createAttachment(callback.getFile().getVirtualFile()));
    }
  }

  public static void expandTemplate(@NotNull String key,
                                    @NotNull CustomTemplateCallback callback,
                                    @NotNull Editor editor,
                                    @NotNull PostfixTemplateProvider provider,
                                    @NotNull PostfixTemplate postfixTemplate) {
    ThreadingAssertions.assertEventDispatchThread();
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.postfix");
    final PsiFile file = callback.getContext().getContainingFile();
    if (isApplicableTemplate(provider, key, file, editor, postfixTemplate)) {
      int offset = deleteTemplateKey(file, editor, key);
      try {
        provider.preExpand(file, editor);
        PsiElement context = CustomTemplateCallback.getContext(file, positiveOffset(offset));
        expandTemplate(postfixTemplate, editor, context);
      }
      finally {
        provider.afterExpand(file, editor);
      }
    }
    // don't care about errors in multiCaret mode
    else if (editor.getCaretModel().getAllCarets().size() == 1) {
      LOG.error("Template not found by key: " + key + "; offset = " + callback.getOffset(),
                CoreAttachmentFactory.createAttachment(callback.getFile().getVirtualFile()));
    }
  }

  @Override
  public boolean isApplicable(@NotNull CustomTemplateCallback callback, int offset, boolean wrapping) {
    PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    if (wrapping || !settings.isPostfixTemplatesEnabled()) {
      return false;
    }
    PsiFile contextFile = callback.getFile();
    Language language = PsiUtilCore.getLanguageAtOffset(contextFile, offset);
    CharSequence fileText = callback.getEditor().getDocument().getImmutableCharSequence();
    for (PostfixTemplateProvider provider : LanguagePostfixTemplate.LANG_EP.allForLanguage(language)) {
      if (StringUtil.isNotEmpty(computeTemplateKeyWithoutContextChecking(provider, fileText, offset + 1))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean supportsWrapping() {
    return false;
  }

  @Override
  public void wrap(@NotNull String selection, @NotNull CustomTemplateCallback callback) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull @NlsActions.ActionText String getTitle() {
    return CodeInsightBundle.message("postfix.live.template.title");
  }

  @Override
  public char getShortcut() {
    return (char)PostfixTemplatesSettings.getInstance().getShortcut();
  }

  @Override
  public boolean hasCompletionItem(@NotNull CustomTemplateCallback callback, int offset) {
    return true;
  }

  @Override
  public @NotNull Collection<? extends CustomLiveTemplateLookupElement> getLookupElements(@NotNull PsiFile file,
                                                                                          @NotNull Editor editor,
                                                                                          int offset) {
    Collection<CustomLiveTemplateLookupElement> result = new HashSet<>();
    CustomTemplateCallback callback = new CustomTemplateCallback(editor, file);
    Disposable parentDisposable = Disposer.newDisposable();
    try {
      for (PostfixTemplateProvider provider : LanguagePostfixTemplate.LANG_EP.allForLanguage(getLanguage(callback))) {
        ProgressManager.checkCanceled();
        String key = computeTemplateKeyWithoutContextChecking(callback);
        if (key != null && editor.getCaretModel().getCaretCount() == 1) {
          Condition<PostfixTemplate> isApplicationTemplateFunction =
            createIsApplicationTemplateFunction(provider, key, file, editor, parentDisposable);
          for (PostfixTemplate postfixTemplate : PostfixTemplatesUtils.getAvailableTemplates(provider)) {
            ProgressManager.checkCanceled();
            if (isApplicationTemplateFunction.value(postfixTemplate)) {
              result.add(new PostfixTemplateLookupElement(this, postfixTemplate, postfixTemplate.getKey(), provider, false));
            }
          }
        }
      }
    }
    finally {
      Disposer.dispose(parentDisposable);
    }

    return result;
  }

  private static void expandTemplate(final @NotNull PostfixTemplate template,
                                     final @NotNull Editor editor,
                                     final @NotNull PsiElement context) {
    PostfixTemplateLogger.log(template, context);
    if (template.startInWriteAction()) {
      ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance()
                                                                               .executeCommand(context.getProject(),
                                                                                               () -> template.expand(context, editor),
                                                                                               CodeInsightBundle.message("command.expand.postfix.template"),
                                                                                               POSTFIX_TEMPLATE_ID));
    }
    else {
      template.expand(context, editor);
    }
  }


  private static int deleteTemplateKey(final @NotNull PsiFile file, final @NotNull Editor editor, final @NotNull String key) {
    ThreadingAssertions.assertEventDispatchThread();

    final int currentOffset = editor.getCaretModel().getOffset();
    final int newOffset = currentOffset - key.length();
    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().runUndoTransparentAction(() -> {
      Document document = editor.getDocument();
      document.deleteString(newOffset, currentOffset);
      editor.getCaretModel().moveToOffset(newOffset);
      PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
    }));
    return newOffset;
  }

  private static Condition<PostfixTemplate> createIsApplicationTemplateFunction(final @NotNull PostfixTemplateProvider provider,
                                                                                @NotNull String key,
                                                                                @NotNull PsiFile file,
                                                                                @NotNull Editor editor,
                                                                                @NotNull Disposable parentDisposable) {
    if (file.getFileType().isBinary()) {
      return Conditions.alwaysFalse();
    }

    int currentOffset = editor.getCaretModel().getOffset();
    final int newOffset = currentOffset - key.length();
    CharSequence fileContent = editor.getDocument().getCharsSequence();
    StringBuilder fileContentWithoutKey = new StringBuilder();
    fileContentWithoutKey.append(fileContent.subSequence(0, newOffset));
    fileContentWithoutKey.append(fileContent.subSequence(currentOffset, fileContent.length()));
    PsiFile copyFile = copyFile(file, fileContentWithoutKey);

    copyFile = provider.preCheck(copyFile, editor, newOffset);
    Document copyDocument = copyFile.getFileDocument();

    // The copy document doesn't contain live template key.
    // Register offset translator to make getOriginalElement() work in the copy.
    Document fileDocument = file.getFileDocument();
    if (fileDocument.getTextLength() < currentOffset) {
      LOG.error("File document length (" + fileDocument.getTextLength() + ") is less than offset (" + currentOffset + ")",
                CoreAttachmentFactory.createAttachment(fileDocument), CoreAttachmentFactory.createAttachment(editor.getDocument()));
    }
    Document originalDocument = editor.getDocument();
    OffsetTranslator translator = new OffsetTranslator(originalDocument, file, copyDocument, newOffset, currentOffset, "");
    Disposer.register(parentDisposable, translator);

    final PsiElement context = CustomTemplateCallback.getContext(copyFile, positiveOffset(newOffset));
    final Document finalCopyDocument = copyDocument;
    return template -> template != null && isDumbEnough(template, context) &&
                       template.isEnabled(provider) && template.isApplicable(context, finalCopyDocument, newOffset);
  }

  private static boolean isDumbEnough(@NotNull PostfixTemplate template, @NotNull PsiElement context) {
    DumbService dumbService = DumbService.getInstance(context.getProject());
    return dumbService.isUsableInCurrentContext(template);
  }

  public static @NotNull PsiFile copyFile(@NotNull PsiFile file, @NotNull StringBuilder fileContentWithoutKey) {
    PsiFileFactory psiFileFactory = PsiFileFactory.getInstance(file.getProject());
    FileType fileType = file.getFileType();
    Language language = LanguageUtil.getLanguageForPsi(file.getProject(), file.getVirtualFile(), fileType);
    PsiFile copy = language != null ? psiFileFactory.createFileFromText(file.getName(), language, fileContentWithoutKey, false, true)
                                    : psiFileFactory.createFileFromText(file.getName(), fileType, fileContentWithoutKey);

    if (copy instanceof PsiFileImpl) {
      ((PsiFileImpl)copy).setOriginalFile(TemplateLanguageUtil.getBaseFile(file));
    }

    VirtualFile vFile = copy.getVirtualFile();
    if (vFile != null) {
      UndoUtil.disableUndoFor(vFile);
    }
    return copy;
  }

  public static boolean isApplicableTemplate(@NotNull PostfixTemplateProvider provider,
                                             @NotNull String key,
                                             @NotNull PsiFile file,
                                             @NotNull Editor editor) {
    return findApplicableTemplate(provider, key, editor, file) != null;
  }

  private static boolean isApplicableTemplate(@NotNull PostfixTemplateProvider provider,
                                              @NotNull String key,
                                              @NotNull PsiFile file,
                                              @NotNull Editor editor,
                                              @Nullable PostfixTemplate template) {
    Disposable parentDisposable = Disposer.newDisposable();
    try {
      return createIsApplicationTemplateFunction(provider, key, file, editor, parentDisposable).value(template);
    }
    finally {
      Disposer.dispose(parentDisposable);
    }
  }

  private static @NotNull Set<String> getKeys(@NotNull PostfixTemplateProvider provider) {
    Set<String> result = new HashSet<>();
    for (PostfixTemplate template : PostfixTemplatesUtils.getAvailableTemplates(provider)) {
      result.add(template.getKey());
    }
    return result;
  }

  private static @Nullable PostfixTemplate findApplicableTemplate(@NotNull PostfixTemplateProvider provider,
                                                                  @Nullable String key,
                                                                  @NotNull Editor editor,
                                                                  @NotNull PsiFile file) {
    for (PostfixTemplate template : PostfixTemplatesUtils.getAvailableTemplates(provider)) {
      if (template.getKey().equals(key) && isApplicableTemplate(provider, key, file, editor, template)) {
        return template;
      }
    }
    return null;
  }

  private static Language getLanguage(@NotNull CustomTemplateCallback callback) {
    return PsiUtilCore.getLanguageAtOffset(callback.getFile(), callback.getOffset());
  }

  private static int positiveOffset(int offset) {
    return offset > 0 ? offset - 1 : offset;
  }
}
