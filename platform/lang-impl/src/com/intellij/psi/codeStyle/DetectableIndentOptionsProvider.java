// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import static com.intellij.psi.codeStyle.DetectAndAdjustIndentOptionsTask.getDefaultIndentOptions;

/**
 * @author Rustam Vishnyakov
 */
@ApiStatus.Internal
public class DetectableIndentOptionsProvider extends FileIndentOptionsProvider {
  private boolean myIsEnabledInTest;
  private final Map<VirtualFile, IndentOptions> myDiscardedOptions = new WeakHashMap<>();

  @Override
  public @Nullable IndentOptions getIndentOptions(@NotNull Project project, @NotNull CodeStyleSettings settings, @NotNull VirtualFile file) {
    if (!isEnabled(settings, file)) {
      return null;
    }

    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) {
      return null;
    }

    TimeStampedIndentOptions options;
    synchronized (document) {
      options = getValidCachedIndentOptions(project, file, document);

      if (options != null) {
        return options;
      }

      options = getDefaultIndentOptions(project, file, document);
      options.associateWithDocument(document);
    }

    scheduleDetectionInBackground(project, document, options);

    return options;
  }

  protected void scheduleDetectionInBackground(@NotNull Project project,
                                               @NotNull Document document,
                                               @NotNull TimeStampedIndentOptions options)
  {
    new DetectAndAdjustIndentOptionsTask(project, document, options).scheduleInBackgroundForCommittedDocument();
  }

  @Override
  public boolean useOnFullReformat() {
    return false;
  }

  @TestOnly
  public void setEnabledInTest(boolean isEnabledInTest) {
    myIsEnabledInTest = isEnabledInTest;
  }

  private boolean isEnabled(@NotNull CodeStyleSettings settings, @NotNull VirtualFile file) {
    if (!file.isValid() ||
        !file.isWritable() ||
        ScratchUtil.isScratch(file)) {
      return false;
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myIsEnabledInTest;
    }
    if (file instanceof LightVirtualFile || myDiscardedOptions.containsKey(file)) {
      return false;
    }
    return hasFormattingModelBuilder(file) && settings.AUTODETECT_INDENTS;
  }

  private static boolean hasFormattingModelBuilder(@NotNull VirtualFile file) {
    FileType fileType = file.getFileType();
    if (fileType instanceof LanguageFileType) {
      Language language = ((LanguageFileType)fileType).getLanguage();
      return LanguageFormatting.INSTANCE.forLanguage(language) != null;
    }
    return false;
  }

  @TestOnly
  public static @Nullable DetectableIndentOptionsProvider getInstance() {
    return EP_NAME.findExtension(DetectableIndentOptionsProvider.class);
  }


  private void disableForFile(@NotNull VirtualFile file, @NotNull IndentOptions indentOptions) {
    myDiscardedOptions.put(file, indentOptions);
  }

  public TimeStampedIndentOptions getValidCachedIndentOptions(@NotNull Project project, @NotNull VirtualFile virtualFile, Document document) {
    IndentOptions options = IndentOptions.retrieveFromAssociatedDocument(document);
    if (options instanceof TimeStampedIndentOptions cachedInDocument) {
      final IndentOptions defaultIndentOptions = getDefaultIndentOptions(project, virtualFile, document);
      if (!cachedInDocument.isOutdated(document, defaultIndentOptions)) {
        return cachedInDocument;
      }
    }
    return null;
  }

  public static void showDisabledDetectionNotification(@NotNull Project project) {
    DetectionDisabledNotification notification = new DetectionDisabledNotification(project);
    notification.notify(project);
  }

  private static final class DetectionDisabledNotification extends Notification {
    private DetectionDisabledNotification(Project project) {
      super("Automatic indent detection",
            ApplicationBundle.message("code.style.indent.detector.notification.content"), "",
            NotificationType.INFORMATION);
      addAction(new ReEnableDetection(project, this));
      addAction(new ShowIndentDetectionOptionAction(ApplicationBundle.message("code.style.indent.provider.notification.settings")));
    }
  }

  private static final class ShowIndentDetectionOptionAction extends DumbAwareAction {
    private ShowIndentDetectionOptionAction(@Nullable @NlsActions.ActionText String text) {
      super(text);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ShowSettingsUtilImpl.showSettingsDialog(e.getProject(), "preferences.sourceCode", "detect indent");
    }
  }

  private static final class ReEnableDetection extends DumbAwareAction {
    private final Project myProject;
    private final Notification myNotification;

    private ReEnableDetection(@NotNull Project project, Notification notification) {
      super(ApplicationBundle.message("code.style.indent.provider.notification.re.enable"));
      myProject = project;
      myNotification = notification;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myNotification.expire();
      setIndentDetectionEnabled(myProject, true);
    }
  }

  private static boolean areDetected(@NotNull IndentOptions indentOptions) {
    return indentOptions instanceof TimeStampedIndentOptions && ((TimeStampedIndentOptions)indentOptions).isDetected();
  }

  @Override
  public @Nullable IndentStatusBarUIContributor getIndentStatusBarUiContributor(@NotNull IndentOptions indentOptions) {
    return new MyUIContributor(indentOptions);
  }


  private final class MyUIContributor extends IndentStatusBarUIContributor {
    private MyUIContributor(IndentOptions options) {
      super(options);
    }

    @Override
    public AnAction @Nullable [] getActions(@NotNull PsiFile file) {
      IndentOptions indentOptions = getIndentOptions();
      List<AnAction> actions = new ArrayList<>();
      final VirtualFile virtualFile = file.getVirtualFile();
      final Project project = file.getProject();
      final IndentOptions projectOptions = CodeStyle.getSettings(project).getIndentOptions(file.getFileType());
      final String projectOptionsTip = StringUtil.capitalizeWords(IndentStatusBarUIContributor.getIndentInfo(projectOptions), true);
      if (indentOptions instanceof TimeStampedIndentOptions) {
        if (((TimeStampedIndentOptions)indentOptions).isDetected()) {
          actions.add(
            DumbAwareAction.create(
              ApplicationBundle.message("code.style.indent.detector.reject", projectOptionsTip),
              e -> {
                disableForFile(virtualFile, indentOptions);
                CodeStyleSettingsManager.getInstance(project).fireCodeStyleSettingsChanged(virtualFile);
              }));
          final var reindentActionText = ApplicationBundle.message("code.style.indent.detector.reindent", projectOptionsTip);
          actions.add(
            DumbAwareAction.create(reindentActionText,
                                   e -> {
                                     disableForFile(virtualFile, indentOptions);
                                     final var document = FileDocumentManager.getInstance().getCachedDocument(virtualFile);
                                     if (document != null) {
                                       // IDEA-332405 -- make sure that detected indent options are not used for the "reindent file" action
                                       final var indentOptsWithoutDetected = CodeStyle
                                         .getSettings(project, virtualFile)
                                         .getIndentOptionsByFile(project, virtualFile, null, true, null);
                                       indentOptsWithoutDetected.associateWithDocument(document);
                                     }
                                     CodeStyleSettingsManager.getInstance(project).fireCodeStyleSettingsChanged(virtualFile);
                                     CommandProcessor.getInstance().executeCommand(
                                       project,
                                       () -> ApplicationManager.getApplication().runWriteAction(
                                         () -> {
                                           CodeStyleManager.getInstance(project).adjustLineIndent(file, file.getTextRange());
                                           UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction() {
                                             @Override
                                             public void undo() {
                                               CodeStyleSettingsManager.getInstance(project).fireCodeStyleSettingsChanged(virtualFile);
                                             }

                                             @Override
                                             public void redo() {
                                               CodeStyleSettingsManager.getInstance(project).fireCodeStyleSettingsChanged(virtualFile);
                                             }
                                           });
                                         }),
                                       reindentActionText,
                                       null
                                     );
                                     myDiscardedOptions.remove(virtualFile);
                                   }));
          actions.add(Separator.getInstance());
        }
      }
      else if (virtualFile != null && myDiscardedOptions.containsKey(virtualFile)) {
        final IndentOptions discardedOptions = myDiscardedOptions.get(virtualFile);
        final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document != null) {
          actions.add(
            DumbAwareAction.create(
              ApplicationBundle
                .message("code.style.indent.detector.apply", IndentStatusBarUIContributor.getIndentInfo(discardedOptions),
                         ColorUtil.toHex(JBColor.GRAY)),
              e -> {
                myDiscardedOptions.remove(virtualFile);
                discardedOptions.associateWithDocument(document);
                CodeStyleSettingsManager.getInstance(project).fireCodeStyleSettingsChanged(virtualFile);
              }));
          actions.add(Separator.getInstance());
        }
      }
      return actions.toArray(AnAction.EMPTY_ARRAY);
    }

    @Override
    public @NotNull AnAction createDisableAction(@NotNull Project project) {
      return DumbAwareAction.create(
        ApplicationBundle.message("code.style.indent.detector.disable"),
        e -> {
          myDiscardedOptions.clear();
          setIndentDetectionEnabled(project, false);
        });
    }

    @Override
    public String getHint() {
      if (areDetected(getIndentOptions())) {
        return LangBundle.message("indent.option.detected");
      }
      return null;
    }

    @Override
    public boolean areActionsAvailable(@NotNull VirtualFile file) {
      return
        areDetected(getIndentOptions()) ||
        myDiscardedOptions.containsKey(file);
    }
  }

  public static void setIndentDetectionEnabled(@NotNull Project project, boolean detectionEnabled) {
    CodeStyleSettings settings = CodeStyle.getSettings(project);
    settings.AUTODETECT_INDENTS = detectionEnabled;
    settings.getModificationTracker().incModificationCount();
    CodeStyleSettingsManager.getInstance(project).fireCodeStyleSettingsChanged();
    if (!detectionEnabled)
      showDisabledDetectionNotification(project);
  }
}
