/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.scratch.ScratchFileType;
import com.intellij.lang.LanguageFormatting;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import static com.intellij.psi.codeStyle.DetectAndAdjustIndentOptionsTask.getDefaultIndentOptions;

/**
 * @author Rustam Vishnyakov
 */
public class DetectableIndentOptionsProvider extends FileIndentOptionsProvider {
  private static final ExecutorService BOUNDED_EXECUTOR = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(
    "DetectableIndentOptionsProvider Pool");

  private static final NotificationGroup NOTIFICATION_GROUP =
    new NotificationGroup("Automatic indent detection", NotificationDisplayType.STICKY_BALLOON, true);
  
  private boolean myIsEnabledInTest;
  private final Map<VirtualFile,IndentOptions> myDiscardedOptions = ContainerUtil.createWeakMap();
  private boolean hasBeenAdvertised;

  @Nullable
  @Override
  public IndentOptions getIndentOptions(@NotNull CodeStyleSettings settings, @NotNull PsiFile file) {
    if (!isEnabled(settings, file)) {
      return null;
    }

    Project project = file.getProject();
    PsiDocumentManager psiManager = PsiDocumentManager.getInstance(project);
    Document document = psiManager.getDocument(file);
    if (document == null) {
      return null;
    }

    TimeStampedIndentOptions options;
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (document) {
      options = getValidCachedIndentOptions(file, document);

      if (options != null) {
        return options;
      }

      options = getDefaultIndentOptions(file, document);
      options.associateWithDocument(document);
    }

    scheduleDetectionInBackground(project, document, options);

    return options;
  }

  protected void scheduleDetectionInBackground(@NotNull Project project,
                                               @NotNull Document document,
                                               @NotNull TimeStampedIndentOptions options)
  {
    DetectAndAdjustIndentOptionsTask task = new DetectAndAdjustIndentOptionsTask(project, document, options, BOUNDED_EXECUTOR);
    task.scheduleInBackgroundForCommittedDocument();
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
    if (file instanceof PsiCompiledFile || file.getFileType() == ScratchFileType.INSTANCE) return false;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myIsEnabledInTest;
    }
    VirtualFile vFile = file.getVirtualFile();
    if (vFile == null || vFile instanceof LightVirtualFile || myDiscardedOptions.containsKey(vFile)) return false;
    return LanguageFormatting.INSTANCE.forContext(file) != null && settings.AUTODETECT_INDENTS;
  }

  @TestOnly
  @Nullable
  public static DetectableIndentOptionsProvider getInstance() {
    return FileIndentOptionsProvider.EP_NAME.findExtension(DetectableIndentOptionsProvider.class);
  }

  @Nullable
  @Override
  public AnAction[] getActions(@NotNull PsiFile file, @NotNull IndentOptions indentOptions) {
    List<AnAction> actions = ContainerUtil.newArrayList();
    final VirtualFile virtualFile = file.getVirtualFile();
    final Project project = file.getProject();
    final IndentOptions projectOptions = CodeStyle.getSettings(project).getIndentOptions(file.getFileType());
    final String projectOptionsTip = FileIndentOptionsProvider.getTooltip(projectOptions, "project");
    if (indentOptions instanceof TimeStampedIndentOptions) {
      if (((TimeStampedIndentOptions)indentOptions).isDetected()) {
        actions.add(
          new AnAction(ApplicationBundle.message("code.style.indent.detector.reject", projectOptionsTip)) {
            @Override
            public void actionPerformed(AnActionEvent e) {
              disableForFile(virtualFile, indentOptions);
              notifyIndentOptionsChanged(project, file);
            }
          }
        );
        actions.add(
          new AnAction(ApplicationBundle.message("code.style.indent.detector.reindent", projectOptionsTip)) {
            @Override
            public void actionPerformed(AnActionEvent e) {
              disableForFile(virtualFile, indentOptions);
              notifyIndentOptionsChanged(project, file);
              CommandProcessor.getInstance().runUndoTransparentAction(
                () -> ApplicationManager.getApplication().runWriteAction(
                  () -> CodeStyleManager.getInstance(project).adjustLineIndent(file, file.getTextRange()))
              );
              myDiscardedOptions.remove(virtualFile);
            }
          }
        );
        actions.add(
          new AnAction(ApplicationBundle.message("code.style.indent.detector.disable")) {
            @Override
            public void actionPerformed(AnActionEvent e) {
              CodeStyle.getSettings(project).AUTODETECT_INDENTS = false;
              notifyIndentOptionsChanged(project, null);
              showDisabledDetectionNotification(project);
            }
          }
        );
      }
    }
    else if (myDiscardedOptions.containsKey(virtualFile)) {
      final IndentOptions discardedOptions = myDiscardedOptions.get(virtualFile);
      final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      if (document != null) {
        actions.add(
          new AnAction(ApplicationBundle.message("code.style.indent.detector.apply", getTooltip(discardedOptions))) {
            @Override
            public void actionPerformed(AnActionEvent e) {
              myDiscardedOptions.remove(virtualFile);
              discardedOptions.associateWithDocument(document);
              notifyIndentOptionsChanged(project, file);
            }
          }
        );
      }
    }
    return ContainerUtil.toArray(actions, AnAction.EMPTY_ARRAY);
  }

  private void disableForFile(@NotNull VirtualFile file, @NotNull IndentOptions indentOptions) {
    myDiscardedOptions.put(file, indentOptions);
  }

  public TimeStampedIndentOptions getValidCachedIndentOptions(PsiFile file, Document document) {
    IndentOptions options = IndentOptions.retrieveFromAssociatedDocument(file);
    if (options instanceof TimeStampedIndentOptions) {
      final IndentOptions defaultIndentOptions = getDefaultIndentOptions(file, document);
      final TimeStampedIndentOptions cachedInDocument = (TimeStampedIndentOptions)options;
      if (!cachedInDocument.isOutdated(document, defaultIndentOptions)) {
        return cachedInDocument;
      }
    }
    return null;
  }

  private static void notifyIndentOptionsChanged(@NotNull Project project, @Nullable PsiFile file) {
    //noinspection deprecation
    CodeStyleSettingsManager.getInstance(project).fireCodeStyleSettingsChanged(file);
  }

  private static void showDisabledDetectionNotification(@NotNull Project project) {
    DetectionDisabledNotification notification = new DetectionDisabledNotification(project);
    notification.notify(project);
  }

  private static class DetectionDisabledNotification extends Notification {
    public DetectionDisabledNotification(Project project) {
      super(NOTIFICATION_GROUP.getDisplayId(),
            ApplicationBundle.message("code.style.indent.detector.notification.title"),
            ApplicationBundle.message("code.style.indent.detector.notification.content"),
            NotificationType.INFORMATION);
      addAction(new ReEnableDetection(project, this));
      addAction(new ShowIndentDetectionOptionAction(ApplicationBundle.message("code.style.indent.detector.notification.settings")));
    }
  }

  private static class ShowIndentDetectionOptionAction extends DumbAwareAction {
    public ShowIndentDetectionOptionAction(@Nullable String text) {
      super(text);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      ShowSettingsUtilImpl.showSettingsDialog(e.getProject(), "preferences.sourceCode", "detect indent");
    }
  }

  private static class ReEnableDetection extends DumbAwareAction {
    private final Project myProject;
    private final Notification myNotification;

    public ReEnableDetection(@NotNull Project project, Notification notification) {
      super(ApplicationBundle.message("code.style.indent.detector.notification.enable"));
      myProject = project;
      myNotification = notification;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      CodeStyle.getSettings(myProject).AUTODETECT_INDENTS = true;
      notifyIndentOptionsChanged(myProject, null);
      myNotification.expire();
    }
  }

  @Override
  public String getHint(@NotNull IndentOptions indentOptions) {
    if (areDetected(indentOptions)) {
      return "detected";
    }
    return null;
  }

  @Override
  public boolean areActionsAvailable(@NotNull VirtualFile file, @NotNull IndentOptions indentOptions) {
    return
      areDetected(indentOptions) ||
      myDiscardedOptions.containsKey(file);
  }

  @Nullable
  @Override
  public String getAdvertisementText(@NotNull PsiFile psiFile, @NotNull IndentOptions indentOptions) {
    if (areDetected(indentOptions) && !hasBeenAdvertised) {
      String actualOptionsHint = getTooltip(indentOptions, null);
      IndentOptions projectOptions = CodeStyle.getSettings(psiFile.getProject()).getIndentOptions(psiFile.getFileType());
      String projectOptionsHint = getTooltip(projectOptions, null);
      hasBeenAdvertised = true;
      return ApplicationBundle.message("code.style.different.indent.size.detected", actualOptionsHint, projectOptionsHint);
    }
    return null;
  }

  private static boolean areDetected(@NotNull IndentOptions indentOptions) {
    return indentOptions instanceof TimeStampedIndentOptions && ((TimeStampedIndentOptions)indentOptions).isDetected();
  }
}
