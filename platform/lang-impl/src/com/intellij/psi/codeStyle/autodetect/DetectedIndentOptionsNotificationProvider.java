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

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.FileIndentOptionsProvider;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
          if (provider != null &&
              !provider.isAcceptedWithoutWarning(file) &&
              provider.getDisplayName() != null &&
              !userOptions.equals(detectedOptions)) {
            final EditorNotificationPanel panel =
              new EditorNotificationPanel()
                .text(ApplicationBundle.message("code.style.indents.detector.message", provider.getDisplayName(),
                                                getOptionDiffInfoString(userOptions, detectedOptions)));
            if (provider.getIcon() != null) {
              panel.icon(provider.getIcon());
            }
            panel.createActionLabel(
              ApplicationBundle.message("code.style.indents.detector.accept"),
              new Runnable() {
                @Override
                public void run() {
                  provider.setAccepted(file);
                  EditorNotifications.getInstance(project).updateAllNotifications();
                }
              }
            );
            if (provider.canBeDisabled()) {
              panel.createActionLabel(
                ApplicationBundle.message("code.style.indents.detector.disable"),
                new Runnable() {
                  @Override
                  public void run() {
                    provider.disable(project);
                    if (editor instanceof EditorEx) {
                      ((EditorEx)editor).reinitSettings();
                    }
                    EditorNotifications.getInstance(project).updateAllNotifications();
                  }
                }
              );
            }
            return panel;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static String getOptionDiffInfoString(CommonCodeStyleSettings.IndentOptions user,
                                                CommonCodeStyleSettings.IndentOptions detected) {
    StringBuilder sb = new StringBuilder();
    if (user.INDENT_SIZE != detected.INDENT_SIZE) {
      sb.append("indent size=").append(detected.INDENT_SIZE);
    }
    if (user.TAB_SIZE != detected.TAB_SIZE) {
      if (sb.length() > 0) sb.append(", ");
      sb.append("tab size=").append(detected.TAB_SIZE);
    }
    if (user.USE_TAB_CHARACTER != detected.USE_TAB_CHARACTER) {
      if (sb.length() > 0) sb.append(", ");
      sb.append(detected.USE_TAB_CHARACTER ? "tabs" : "no tabs");
    }
    if (user.SMART_TABS != detected.SMART_TABS) {
      if (sb.length() > 0) sb.append(", ");
      sb.append(detected.SMART_TABS ? "smart tabs" : "no smart tabs");
    }
    return sb.toString();
  }
}
