/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.codeInsight.template.CustomLiveTemplateBase;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings;
import com.intellij.codeInsight.template.postfix.util.Aliases;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class PostfixLiveTemplate extends CustomLiveTemplateBase {
  public static final String POSTFIX_TEMPLATE_ID = "POSTFIX_TEMPLATE_ID";

  private static final Logger LOG = Logger.getInstance(PostfixLiveTemplate.class);
  private final HashMap<String, PostfixTemplate> myTemplates = ContainerUtil.newHashMap();

  public PostfixLiveTemplate() {
    for (PostfixTemplate template : PostfixTemplate.EP_NAME.getExtensions()) {
      register(template.getKey(), template);
      Aliases aliases = template.getClass().getAnnotation(Aliases.class);
      if (aliases != null) {
        for (String key : aliases.value()) {
          register(key, template);
        }
      }
    }
  }

  private void register(@NotNull String key, @NotNull PostfixTemplate template) {
    PostfixTemplate registered = myTemplates.put(key, template);
    if (registered != null) {
      LOG.error("Can't register postfix template. Duplicated key: " + template.getKey());
    }
  }

  @Nullable
  @Override
  public String computeTemplateKey(@NotNull CustomTemplateCallback callback) {
    Editor editor = callback.getEditor();
    String key = computeTemplateKeyWithoutContextChecking(editor.getDocument().getCharsSequence(), editor.getCaretModel().getOffset());
    if (key == null) return null;
    return isApplicableTemplate(getTemplateByKey(key), key, callback.getContext().getContainingFile(), editor) ? key : null;
  }
  
  @Nullable
  public String computeTemplateKeyWithoutContextChecking(@NotNull CharSequence documentContent, int currentOffset) {
    int startOffset = currentOffset;
    while (startOffset > 0) {
      char currentChar = documentContent.charAt(startOffset - 1);
      if (!Character.isJavaIdentifierPart(currentChar)) {
        if (currentChar != '.' && currentChar != '!') {
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
  public void expand(@NotNull final String key, @NotNull final CustomTemplateCallback callback) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final PostfixTemplate template = getTemplateByKey(key);
    final Editor editor = callback.getEditor();
    final PsiFile file = callback.getContext().getContainingFile();
    if (isApplicableTemplate(template, key, file, editor)) {
      int currentOffset = editor.getCaretModel().getOffset();
      PsiElement newContext = deleteTemplateKey(file, editor.getDocument(), currentOffset, key);
      newContext = addSemicolonIfNeeded(editor, editor.getDocument(), newContext, currentOffset - key.length());
      expandTemplate(template, editor, newContext);
    }
    else {
      LOG.error("Template not found by key: " + key);
    }
  }

  @Override
  public boolean isApplicable(PsiFile file, int offset, boolean wrapping) {
    PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    if (wrapping  || file == null || settings == null || !settings.isPostfixTemplatesEnabled() ||
        PsiUtilCore.getLanguageAtOffset(file, offset) != JavaLanguage.INSTANCE) {
      return false;
    }
    return StringUtil.isNotEmpty(computeTemplateKeyWithoutContextChecking(file.getText(), offset + 1));
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

  @Nullable
  public PostfixTemplate getTemplateByKey(@NotNull String key) {
    return myTemplates.get(key);
  }

  private static void expandTemplate(@NotNull final PostfixTemplate template,
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

  @Contract("null, _, _, _ -> false")
  private static boolean isApplicableTemplate(@Nullable PostfixTemplate template, @NotNull String key, @NotNull PsiFile file, @NotNull Editor editor) {
    if (template == null || !template.isEnabled()) {
      return false;
    }

    int currentOffset = editor.getCaretModel().getOffset();
    int newOffset = currentOffset - key.length();
    CharSequence fileContent = editor.getDocument().getCharsSequence();

    StringBuilder fileContentWithoutKey = new StringBuilder();
    fileContentWithoutKey.append(fileContent.subSequence(0, newOffset));
    fileContentWithoutKey.append(fileContent.subSequence(currentOffset, fileContent.length()));
    PsiFile copyFile = copyFile(file, fileContentWithoutKey);
    Document copyDocument = copyFile.getViewProvider().getDocument();
    if (copyDocument == null) {
      return false;
    }

    if (isSemicolonNeeded(copyFile, editor)) {
      fileContentWithoutKey.insert(newOffset, ';');
      copyFile = copyFile(file, fileContentWithoutKey);
      copyDocument = copyFile.getViewProvider().getDocument();
      if (copyDocument == null) {
        return false;
      }
    }

    PsiElement context = CustomTemplateCallback.getContext(copyFile, newOffset > 0 ? newOffset - 1 : newOffset);
    return template.isApplicable(context, copyDocument, newOffset);
  }

  @NotNull
  private static PsiFile copyFile(@NotNull PsiFile file, @NotNull StringBuilder fileContentWithoutKey) {
    final PsiFileFactory psiFileFactory = PsiFileFactory.getInstance(file.getProject());
    PsiFile copy = psiFileFactory.createFileFromText(file.getName(), file.getFileType(), fileContentWithoutKey);
    VirtualFile vFile = copy.getVirtualFile();
    if (vFile != null) {
      vFile.putUserData(UndoConstants.DONT_RECORD_UNDO, Boolean.TRUE);
    }
    return copy;
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

  @NotNull
  private static PsiElement addSemicolonIfNeeded(@NotNull final Editor editor,
                                                 @NotNull final Document document,
                                                 @NotNull final PsiElement context,
                                                 final int offset) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final Ref<PsiElement> newContext = Ref.create(context);
    final PsiFile file = context.getContainingFile();
    if (isSemicolonNeeded(file, editor)) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
            public void run() {
              document.insertString(offset, ";");
              PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
              newContext.set(CustomTemplateCallback.getContext(file, offset - 1));
            }
          });
        }
      });
    }
    return newContext.get();
  }

  private static boolean isSemicolonNeeded(@NotNull PsiFile file, @NotNull Editor editor) {
    CompletionInitializationContext initializationContext = new CompletionInitializationContext(editor, file, CompletionType.BASIC);
    new JavaCompletionContributor().beforeCompletion(initializationContext);
    return StringUtil.endsWithChar(initializationContext.getDummyIdentifier(), ';');
  }
}
