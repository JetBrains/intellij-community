/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.autodetect;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.*;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.codeStyle.EditorNotificationInfo.*;

/**
 * @author Rustam Vishnyakov
 */
public class DetectedIndentOptionsNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("indent.options.notification.provider");

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull final VirtualFile file, @NotNull FileEditor fileEditor) {
    if (fileEditor instanceof TextEditor) {
      final Editor editor = ((TextEditor)fileEditor).getEditor();
      final Project project = editor.getProject();
      if (project != null) {
        Document document = editor.getDocument();
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        PsiFile psiFile = documentManager.getPsiFile(document);
        final Ref<FileIndentOptionsProvider> indentOptionsProviderRef = new Ref<FileIndentOptionsProvider>();
        if (psiFile != null) {
          CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
          CommonCodeStyleSettings.IndentOptions userOptions = settings.getIndentOptions(psiFile.getFileType());
          CommonCodeStyleSettings.IndentOptions detectedOptions = CodeStyleSettingsManager.getSettings(project).getIndentOptionsByFile(
            psiFile, null, true,
            new Processor<FileIndentOptionsProvider>() {
              @Override
              public boolean process(FileIndentOptionsProvider provider) {
                indentOptionsProviderRef.set(provider);
                return false;
              }
            });
          final FileIndentOptionsProvider provider = indentOptionsProviderRef.get();
          EditorNotificationInfo info = provider != null && !provider.isAcceptedWithoutWarning(file) && !userOptions.equals(detectedOptions)
                                        ? provider.getNotificationInfo(project, file, fileEditor, userOptions, detectedOptions)
                                        : null;

          if (info != null) {
            EditorNotificationPanel panel = new EditorNotificationPanel().text(info.getTitle());
            if (info.getIcon() != null) {
              panel.icon(info.getIcon());
            }
            for (final LabelWithAction actionLabelData : info.getLabelAndActions()) {
              Runnable onClickAction = actionLabelData.action;
              if (actionLabelData.updateAllNotificationsOnFinish) {
                onClickAction = new Runnable() {
                  @Override
                  public void run() {
                    actionLabelData.action.run();
                    EditorNotifications.getInstance(project).updateAllNotifications();
                  }
                };
              }
              panel.createActionLabel(actionLabelData.label, onClickAction);
            }
            return panel;
          }
        }
      }
    }
    return null;
  }
}
