// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingListener;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public final class WrapWithCustomTemplateAction extends AnAction {
  private final CustomLiveTemplate myTemplate;
  private final Editor myEditor;
  private final @Nullable Runnable myAfterExecutionCallback;
  private final PsiFile myFile;

  public WrapWithCustomTemplateAction(CustomLiveTemplate template,
                                      final Editor editor,
                                      final PsiFile file,
                                      final Set<? super Character> usedMnemonicsSet) {
    this(template, editor, file, usedMnemonicsSet, null);
  }

  public WrapWithCustomTemplateAction(CustomLiveTemplate template,
                                      final Editor editor,
                                      final PsiFile file,
                                      final Set<? super Character> usedMnemonicsSet,
                                      @Nullable Runnable afterExecutionCallback) {
    super(InvokeTemplateAction.extractMnemonic(template.getTitle(), usedMnemonicsSet));
    myTemplate = template;
    myFile = file;
    myEditor = editor;
    myAfterExecutionCallback = afterExecutionCallback;
  }


  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    perform();
  }

  public void perform() {
    final Document document = myEditor.getDocument();
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null) {
      ReadonlyStatusHandler.getInstance(myFile.getProject()).ensureFilesWritable(Collections.singletonList(file));
    }

    String selection = myEditor.getSelectionModel().getSelectedText(true);

    if (selection != null) {
      selection = selection.trim();
      PsiDocumentManager.getInstance(myFile.getProject()).commitAllDocuments();
      myTemplate.wrap(selection, new CustomTemplateCallback(myEditor, myFile) {
        @Override
        public void startTemplate(@NotNull Template template, Map<String, String> predefinedValues, TemplateEditingListener listener) {
          super.startTemplate(template, predefinedValues, listener);
          if (myAfterExecutionCallback != null) {
            myAfterExecutionCallback.run();
          }
        }
      });
    }
  }
}
