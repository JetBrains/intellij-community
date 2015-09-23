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
package com.intellij.psi.codeStyle.autodetect;

import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.*;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

import static com.intellij.psi.codeStyle.EditorNotificationInfo.ActionLabelData;

/**
 * @author Rustam Vishnyakov
 */
public class DetectableIndentOptionsProvider extends FileIndentOptionsProvider implements ProviderForCommittedDocument {
  private boolean myIsEnabledInTest;
  private final List<VirtualFile> myAcceptedFiles = new WeakList<VirtualFile>();
  private final List<VirtualFile> myDisabledFiles = new WeakList<VirtualFile>();

  @Nullable
  @Override
  public CommonCodeStyleSettings.IndentOptions getIndentOptions(@NotNull CodeStyleSettings settings, @NotNull PsiFile file) {
    return isDocumentCommitted(file) && isEnabled(settings, file)
           ? new IndentOptionsDetectorImpl(file).getIndentOptions()
           : null;
  }

  private static boolean isDocumentCommitted(@NotNull PsiFile file) {
    PsiDocumentManager manager = PsiDocumentManager.getInstance(file.getProject());
    Document document = manager.getDocument(file);
    return document != null && manager.isCommitted(document);
  }

  @Override
  public boolean useOnFullReformat() {
    return false;
  }

  @TestOnly
  public void setEnabledInTest(boolean isEnabledInTest) {
    myIsEnabledInTest = isEnabledInTest;
  }

  private boolean isEnabled(@NotNull CodeStyleSettings settings, @NotNull PsiFile file) {
    if (file instanceof PsiCompiledFile) return false;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myIsEnabledInTest;
    }
    VirtualFile vFile = file.getVirtualFile();
    if (vFile == null || vFile instanceof LightVirtualFile || myDisabledFiles.contains(vFile)) return false;
    return LanguageFormatting.INSTANCE.forContext(file) != null && settings.AUTODETECT_INDENTS;
  }

  @TestOnly
  @Nullable
  public static DetectableIndentOptionsProvider getInstance() {
    return FileIndentOptionsProvider.EP_NAME.findExtension(DetectableIndentOptionsProvider.class);
  }

  @Nullable
  @Override
  public EditorNotificationInfo getNotificationInfo(@NotNull final Project project,
                                                    @NotNull final VirtualFile file,
                                                    @NotNull final FileEditor fileEditor,
                                                    @NotNull CommonCodeStyleSettings.IndentOptions userOptions,
                                                    @NotNull CommonCodeStyleSettings.IndentOptions detectedOptions)
  {
    final NotificationLabels labels = getNotificationLabels(userOptions, detectedOptions);
    final Editor editor = fileEditor instanceof TextEditor ? ((TextEditor)fileEditor).getEditor() : null;
    if (labels == null || editor == null) return null;

    ActionLabelData okAction = new ActionLabelData(
      ApplicationBundle.message("code.style.indents.detector.accept"),
      new Runnable() {
        @Override
        public void run() {
          setAccepted(file);
        }
      }
    );

    ActionLabelData disableForSingleFile = new ActionLabelData(
      labels.revertToOldSettingsLabel,
      new Runnable() {
        @Override
        public void run() {
          disableForFile(file);
          if (editor instanceof EditorEx) {
            ((EditorEx)editor).reinitSettings();
          }
        }
      }
    );

    ActionLabelData showSettings = new ActionLabelData(
      ApplicationBundle.message("code.style.indents.detector.show.settings"),
      new Runnable() {
        @Override
        public void run() {
          ShowSettingsUtilImpl.showSettingsDialog(project, "preferences.sourceCode", "detect indent");
        }
      }
    );

    final List<ActionLabelData> actions = ContainerUtil.newArrayList(okAction, disableForSingleFile, showSettings);
    return new EditorNotificationInfo() {
      @NotNull
      @Override
      public List<ActionLabelData> getLabelAndActions() {
        return actions;
      }

      @NotNull
      @Override
      public String getTitle() {
        return labels.title;
      }
    };
  }

  @Nullable
  private static NotificationLabels getNotificationLabels(@NotNull CommonCodeStyleSettings.IndentOptions userOptions,
                                                          @NotNull CommonCodeStyleSettings.IndentOptions detectedOptions) {
    if (userOptions.USE_TAB_CHARACTER) {
      if (!detectedOptions.USE_TAB_CHARACTER) {
        return new NotificationLabels(ApplicationBundle.message("code.style.space.indent.detected", detectedOptions.INDENT_SIZE),
                                                   ApplicationBundle.message("code.style.detector.use.tabs"));
      }
    }
    else {
      String restoreToSpaces = ApplicationBundle.message("code.style.detector.use.spaces", userOptions.INDENT_SIZE);
      if (detectedOptions.USE_TAB_CHARACTER) {
        return new NotificationLabels(ApplicationBundle.message("code.style.tab.usage.detected", userOptions.INDENT_SIZE),
                                                   restoreToSpaces);
      }
      if (userOptions.INDENT_SIZE != detectedOptions.INDENT_SIZE) {
        return new NotificationLabels(ApplicationBundle.message("code.style.different.indent.size.detected", detectedOptions.INDENT_SIZE, userOptions.INDENT_SIZE),
                                                   restoreToSpaces);
      }
    }
    return null;
  }

  private void disableForFile(@NotNull VirtualFile file) {
    myDisabledFiles.add(file);
  }
  
  @Override
  public void setAccepted(@NotNull VirtualFile file) {
    myAcceptedFiles.add(file);
  }

  @Override
  public boolean isAcceptedWithoutWarning(@Nullable Project project, @NotNull VirtualFile file) {
    return !FileIndentOptionsProvider.isShowNotification() || myAcceptedFiles.contains(file);
  }

  private static class NotificationLabels {
    public final String title;
    public final String revertToOldSettingsLabel;

    public NotificationLabels(@NotNull String title, @NotNull String revertToOldSettingsLabel) {
      this.title = title;
      this.revertToOldSettingsLabel = revertToOldSettingsLabel;
    }
  }
}
