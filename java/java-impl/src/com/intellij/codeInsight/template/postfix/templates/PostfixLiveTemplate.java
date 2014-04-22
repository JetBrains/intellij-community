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
package com.intellij.codeInsight.template.postfix.templates;

import com.google.common.collect.Sets;
import com.intellij.codeInsight.template.CustomLiveTemplateBase;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.impl.CustomLiveTemplateLookupElement;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateLookupElement;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class PostfixLiveTemplate extends CustomLiveTemplateBase {
  public static final String POSTFIX_TEMPLATE_ID = "POSTFIX_TEMPLATE_ID";
  private static final Logger LOG = Logger.getInstance(PostfixLiveTemplate.class);
  private static final LanguagePostfixTemplate templates = LanguagePostfixTemplate.INSTANCE;

  @NotNull
  public Set<String> getAllTemplateKeys(PsiFile file, int offset) {
    Set<String> keys = Sets.newHashSet();
    Language language = PsiUtilCore.getLanguageAtOffset(file, offset);

    for (PostfixTemplateProvider provider : templates.allForLanguage(language)) {
      keys.addAll(provider.getKeys());
    }
    return keys;
  }

  public boolean hasNotEmptyKey(PsiFile file, int offset) {
    Language language = PsiUtilCore.getLanguageAtOffset(file, offset);
    for (PostfixTemplateProvider provider : templates.allForLanguage(language)) {
      if (StringUtil
        .isNotEmpty(computeTemplateKeyWithoutContextChecking(provider, file.getText(), offset + 1))) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public String computeTemplateKeyWithoutContextChecking(PostfixTemplateProvider provider,
                                                         @NotNull CharSequence documentContent,
                                                         int currentOffset) {
    int startOffset = currentOffset;
    if (documentContent.length() < startOffset) {
      return null;
    }

    while (startOffset > 0) {
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

  @Nullable
  @Override
  public String computeTemplateKey(@NotNull CustomTemplateCallback callback) {
    Editor editor = callback.getEditor();
    CharSequence charsSequence = editor.getDocument().getCharsSequence();
    int offset = editor.getCaretModel().getOffset();
    Language language = getLanguage(callback);
    for (PostfixTemplateProvider provider : templates.allForLanguage(language)) {
      String key = computeTemplateKeyWithoutContextChecking(provider, charsSequence, offset);
      if (key != null && isApplicableTemplate(provider, key, callback.getFile(), editor)) {
        return key;
      }
    }

    return null;
  }

  @Nullable
  @Override
  public String computeTemplateKeyWithoutContextChecking(@NotNull CustomTemplateCallback callback) {
    Editor editor = callback.getEditor();
    return computeTemplateKeyWithoutContextChecking(callback.getFile(), editor, editor.getCaretModel().getOffset());
  }

  @Override
  public boolean supportsMultiCaret() {
    return false;
  }

  @Nullable
  public String computeTemplateKeyWithoutContextChecking(PsiFile file, Editor editor, int currentOffset) {
    Language language = PsiUtilCore.getLanguageAtOffset(file, currentOffset);
    for (PostfixTemplateProvider provider : templates.allForLanguage(language)) {
      String key = computeTemplateKeyWithoutContextChecking(provider, editor.getDocument().getCharsSequence(), currentOffset);
      if (key != null) return key;
    }
    return null;
  }

  @Override
  public void expand(@NotNull final String key, @NotNull final CustomTemplateCallback callback) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.postfix");

    Editor editor = callback.getEditor();
    Language language = getLanguage(callback);
    for (PostfixTemplateProvider provider : templates.allForLanguage(language)) {
      PostfixTemplate postfixTemplate = provider.get(key);
      if (postfixTemplate != null) {
        expandForProvider(provider, key, callback);
        return;
      }
    }

    // don't care about errors in multiCaret mode
    if (editor.getCaretModel().getAllCarets().size() == 1) {
      LOG.error("Template not found by key: " + key);
    }
  }

  private static Language getLanguage(CustomTemplateCallback callback) {
    return PsiUtilCore.getLanguageAtOffset(callback.getFile(), callback.getEditor().getCaretModel().getOffset());
  }

  public void expandForProvider(
    @NotNull PostfixTemplateProvider provider,
    @NotNull final String key,
    @NotNull final CustomTemplateCallback callback) {
    final Editor editor = callback.getEditor();
    final PsiFile file = callback.getContext().getContainingFile();
    if (isApplicableTemplate(provider, key, file, editor)) {
      int currentOffset = editor.getCaretModel().getOffset();
      PsiElement newContext = deleteTemplateKey(file, editor.getDocument(), currentOffset, key);
      newContext = provider.preExpand(editor, newContext, currentOffset, key);
      PostfixTemplate template = provider.get(key);
      assert template != null;
      expandTemplate(template, editor, newContext);
    }
    // don't care about errors in multiCaret mode
    else if (editor.getCaretModel().getAllCarets().size() == 1) {
      LOG.error("Template not found by key: " + key);
    }
  }

  @Override
  public boolean isApplicable(PsiFile file, int offset, boolean wrapping) {
    PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    if (wrapping || file == null || settings == null || !settings.isPostfixTemplatesEnabled()) {
      return false;
    }
    return hasNotEmptyKey(file, offset);
  }

  @Override
  public boolean supportsWrapping() {
    return false;
  }

  @Override
  public void wrap(@NotNull String selection, @NotNull CustomTemplateCallback callback) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public String getTitle() {
    return "Postfix";
  }

  @Override
  public char getShortcut() {
    PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    return settings != null ? (char)settings.getShortcut() : TemplateSettings.TAB_CHAR;
  }

  @Override
  public boolean hasCompletionItem(@NotNull PsiFile file, int offset) {
    return true;
  }

  @NotNull
  @Override
  public Collection<? extends CustomLiveTemplateLookupElement> getLookupElements(@NotNull PsiFile file,
                                                                                 @NotNull Editor editor,
                                                                                 int offset) {
    Collection<CustomLiveTemplateLookupElement> result = ContainerUtil.newHashSet();
    Language language = PsiUtilCore.getLanguageAtOffset(file, editor.getCaretModel().getOffset());
    for (PostfixTemplateProvider provider : templates.allForLanguage(language)) {
      result.addAll(getLookupElementsForProvider(provider, file, editor, offset));
    }

    return result;
  }

  @NotNull
  private Collection<? extends CustomLiveTemplateLookupElement> getLookupElementsForProvider(
    @NotNull PostfixTemplateProvider provider,
    @NotNull PsiFile file,
    @NotNull Editor editor,
    int offset) {
    String key = computeTemplateKeyWithoutContextChecking(file, editor, offset);
    if (key != null && editor.getCaretModel().getCaretCount() == 1) {
      Collection<CustomLiveTemplateLookupElement> result = ContainerUtil.newHashSet();

      Condition<PostfixTemplate> isApplicationTemplateFunction = createIsApplicationTemplateFunction(provider, key, file, editor);
      for (String postfixKey : provider.getKeys()) {
        PostfixTemplate postfixTemplate = provider.get(postfixKey);
        assert postfixTemplate != null;
        if (isApplicationTemplateFunction.value(postfixTemplate)) {
          result.add(new PostfixTemplateLookupElement(this, postfixTemplate, postfixKey, false));
        }
      }
      return result;
    }
    return Collections.emptyList();
  }


  private static void expandTemplate(
    @NotNull final PostfixTemplate template,
    @NotNull final Editor editor,
    @NotNull final PsiElement context) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        CommandProcessor.getInstance().executeCommand(context.getProject(), new Runnable() {
          public void run() {
            template.expand(context, editor);
          }
        }, "Expand postfix template", POSTFIX_TEMPLATE_ID);
      }
    });
  }


  @NotNull
  private static PsiElement deleteTemplateKey(@NotNull final PsiFile file,
                                              @NotNull final Document document,
                                              final int currentOffset,
                                              @NotNull final String key) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final int startOffset = currentOffset - key.length();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
          public void run() {
            document.deleteString(startOffset, currentOffset);
            PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
          }
        });
      }
    });
    return CustomTemplateCallback.getContext(file, startOffset > 0 ? startOffset - 1 : startOffset);
  }

  private static Condition<PostfixTemplate> createIsApplicationTemplateFunction(
    @NotNull PostfixTemplateProvider provider,
    @NotNull String key,
    @NotNull PsiFile file,
    @NotNull Editor editor) {
    int currentOffset = editor.getCaretModel().getOffset();
    final int newOffset = currentOffset - key.length();
    CharSequence fileContent = editor.getDocument().getCharsSequence();
    StringBuilder fileContentWithoutKey = new StringBuilder();
    fileContentWithoutKey.append(fileContent.subSequence(0, newOffset));
    fileContentWithoutKey.append(fileContent.subSequence(currentOffset, fileContent.length()));
    PsiFile copyFile = copyFile(file, fileContentWithoutKey);
    Document copyDocument = copyFile.getViewProvider().getDocument();
    if (copyDocument == null) {
      //noinspection unchecked
      return Condition.FALSE;
    }

    copyFile = provider.preCheck(editor, copyFile, newOffset);
    copyDocument = copyFile.getViewProvider().getDocument();
    if (copyDocument == null) {
      //noinspection unchecked
      return Condition.FALSE;
    }

    final PsiElement context = CustomTemplateCallback.getContext(copyFile, newOffset > 0 ? newOffset - 1 : newOffset);
    final Document finalCopyDocument = copyDocument;
    return new Condition<PostfixTemplate>() {
      @Override
      public boolean value(PostfixTemplate template) {
        return template != null && template.isEnabled() && template.isApplicable(context, finalCopyDocument, newOffset);
      }
    };
  }


  @NotNull
  public static PsiFile copyFile(@NotNull PsiFile file, @NotNull StringBuilder fileContentWithoutKey) {
    final PsiFileFactory psiFileFactory = PsiFileFactory.getInstance(file.getProject());
    PsiFile copy = psiFileFactory.createFileFromText(file.getName(), file.getFileType(), fileContentWithoutKey);
    VirtualFile vFile = copy.getVirtualFile();
    if (vFile != null) {
      vFile.putUserData(UndoConstants.DONT_RECORD_UNDO, Boolean.TRUE);
    }
    return copy;
  }

  public static boolean isApplicableTemplate(
    @NotNull PostfixTemplateProvider provider,
    @NotNull String key,
    @NotNull PsiFile file,
    @NotNull Editor editor) {
    return createIsApplicationTemplateFunction(provider, key, file, editor).value(provider.get(key));
  }
}
