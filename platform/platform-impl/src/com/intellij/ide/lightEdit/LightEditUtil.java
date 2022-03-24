// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.completion.EmptyCompletionNotifier;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.lightEdit.intentions.openInProject.LightEditOpenInProjectIntention;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

import static com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil.OpenPlace.CommandLine;

public final class LightEditUtil {
  private static final String ENABLED_FILE_OPEN_KEY = "light.edit.file.open.enabled";
  private static final String OPEN_FILE_IN_PROJECT_HREF = "open_file_in_project";

  static final Key<String> CREATION_MESSAGE = Key.create("light.edit.file.creation.message");

  private final static Logger LOG = Logger.getInstance(LightEditUtil.class);

  private static final ThreadLocal<LightEditCommandLineOptions> ourCommandLineOptions = new ThreadLocal<>();

  public static final Key<Boolean> SUGGEST_SWITCH_TO_PROJECT = Key.create("light.edit.suggest.project.switch");

  private LightEditUtil() {
  }

  @Nullable
  public static Project openFile(@NotNull Path path, boolean suggestSwitchToProject) {
    VirtualFile virtualFile = VfsUtil.findFile(path, true);
    if (virtualFile != null) {
      if (suggestSwitchToProject) {
        virtualFile.putUserData(SUGGEST_SWITCH_TO_PROJECT, true);
      }
      Project project = LightEditService.getInstance().openFile(virtualFile);
      LightEditFeatureUsagesUtil.logFileOpen(project, CommandLine);
      return project;
    }
    else {
      return handleNonExisting(path);
    }
  }

  private static @Nullable Project handleNonExisting(@NotNull Path path) {
    if (path.getFileName() == null) {
      LOG.error("No file name is given");
    }
    if (isLightEditEnabled() && path.getNameCount() > 0) {
      String fileName = path.getFileName().toString();
      final Ref<String> creationMessage = Ref.create();
      if (path.getNameCount() > 1) {
        File newFile = path.toFile();
        if (!FileUtil.ensureCanCreateFile(newFile)) {
          creationMessage.set(ApplicationBundle.message("light.edit.file.creation.failed.message", path.toString(), fileName));
        }
      }
      final Project project = ((LightEditServiceImpl)LightEditService.getInstance()).getOrCreateProject();
      ApplicationManager.getApplication().invokeLater(() -> {
        LightEditorInfo editorInfo = LightEditService.getInstance().createNewDocument(path);
        if (creationMessage.get() != null) {
          editorInfo.getFile().putUserData(CREATION_MESSAGE, creationMessage.get());
          EditorNotifications.getInstance(project).updateNotifications(editorInfo.getFile());
        }
      });
      return project;
    }
    return null;
  }

  /**
   * @param file file opened in the editor
   * @return target path of non-existent file that was opened in IDE
   */
  public static @Nullable Path getPreferredSavePathForNonExistentFile(@NotNull VirtualFile file) {
    if (file instanceof LightVirtualFile) {
      LightEditorInfo editorInfo = ContainerUtil.getFirstItem(LightEditService.getInstance().getEditorManager().getEditors(file));
      return editorInfo != null ? editorInfo.getPreferredSavePath() : null;
    }
    return null;
  }

  public static boolean isForceOpenInLightEditMode() {
    LightEditCommandLineOptions options = getCommandLineOptions();
    return options != null && options.myLightEditMode;
  }

  @Nullable
  public static Project getProjectIfCreated() {
    return LightEditService.getInstance().getProject();
  }

  static boolean confirmClose(@NotNull @NlsContexts.DialogMessage String message,
                              @NotNull @NlsContexts.DialogTitle String title,
                              @NotNull LightEditSaveConfirmationHandler handler) {
    final String[] options = {getCloseSave(), getCloseDiscard(), getCloseCancel()};
    int result = Messages.showDialog(requireProject(), message, title, options, 0, Messages.getWarningIcon());
    if (result >= 0) {
      if (getCloseCancel().equals(options[result])) {
        return false;
      }
      else if (getCloseSave().equals(options[result])) {
        handler.onSave();
      }
      else if (getCloseDiscard().equals(options[result])){
        handler.onDiscard();
      }
      return true;
    }
    else {
      return false;
    }
  }

  static @Nullable VirtualFile chooseTargetFile(@NotNull Component parent, @NotNull LightEditorInfo editorInfo) {
    FileSaverDialog saver = FileChooserFactory.getInstance().createSaveFileDialog(new FileSaverDescriptor(
      IdeBundle.message("dialog.title.save.as"),
      IdeBundle.message("label.choose.target.file")
    ), parent);
    VirtualFileWrapper fileWrapper = saver.save(VfsUtil.getUserHomeDir(), editorInfo.getFile().getPresentableName());
    return fileWrapper != null ? fileWrapper.getVirtualFile(true) : null;
  }

  private static @NlsContexts.Button String getCloseSave() {
    return ApplicationBundle.message("light.edit.close.save");
  }

  private static @NlsContexts.Button String getCloseDiscard() {
    return ApplicationBundle.message("light.edit.close.discard");
  }

  private static @NlsContexts.Button String getCloseCancel() {
    return ApplicationBundle.message("light.edit.close.cancel");
  }

  public static void markUnknownFileTypeAsPlainTextIfNeeded(@Nullable Project project, @NotNull VirtualFile file) {
    if (project != null && !project.isDefault() && !LightEdit.owns(project)) {
      return;
    }
    if (isLightEditEnabled()) {
      LightEditFileTypeOverrider.markUnknownFileTypeAsPlainText(file);
    }
  }

  public static boolean isLightEditEnabled() {
    return Registry.is(ENABLED_FILE_OPEN_KEY) && !PlatformUtils.isDataGrip();
  }

  @ApiStatus.Internal
  @NotNull
  public static EmptyCompletionNotifier createEmptyCompletionNotifier() {
    return new EmptyCompletionNotifier() {
      @Override
      public void showIncompleteHint(@NotNull Editor editor, @NotNull @NlsContexts.HintText String text, boolean isDumbMode) {
        HintManager.getInstance().showInformationHint(
          editor,
          StringUtil.escapeXmlEntities(text) + CodeInsightBundle.message("completion.incomplete.light.edit.suffix", OPEN_FILE_IN_PROJECT_HREF),
          e -> {
            if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())
                && OPEN_FILE_IN_PROJECT_HREF.equals(e.getDescription())) {
              VirtualFile file = LightEditService.getInstance().getSelectedFile();
              if (file != null) {
                LightEditOpenInProjectIntention.performOn(Objects.requireNonNull(editor.getProject()), file);
              }
            }
          });
      }
    };
  }

  @Nullable
  public static EditorComposite findEditorComposite(@NotNull FileEditor fileEditor) {
    return ((LightEditServiceImpl)LightEditService.getInstance()).getEditPanel().getTabs().findEditorComposite(fileEditor);
  }

  @Nullable
  public static VirtualFile getPreferredSaveTarget(@NotNull LightEditorInfo editorInfo) {
    if (editorInfo.isNew()) {
      Path preferredPath = editorInfo.getPreferredSavePath();
      if (preferredPath != null) {
        File targetFile = preferredPath.toFile();
        if (FileUtil.createIfDoesntExist(targetFile)) {
          return VfsUtil.findFile(preferredPath, true);
        }
      }
    }
    return null;
  }

  @NotNull
  public static Project requireLightEditProject(@Nullable Project project) {
    if (project == null || !LightEdit.owns(project)) {
      LOG.error("LightEdit project is expected while " + (project != null ? project.getName() : "no project") + " is used instead");
      throw new IllegalStateException("Missing LightEdit project");
    }
    return project;
  }

  public static void forbidServiceInLightEditMode(@Nullable Project project, @NotNull Class<?> serviceClass) {
    if (LightEdit.owns(project)) {
      LOG.error("LightEdit mode lacks tool windows, so " + serviceClass.getName() + " shouldn't be instantiated there. " +
                "Please change the caller to avoid loading the service in LightEdit mode!");
    }
  }

  @NotNull
  static Project requireProject() {
    return requireLightEditProject(LightEditService.getInstance().getProject());
  }

  public static <T> @NotNull T computeWithCommandLineOptions(boolean shouldWait,
                                                             boolean lightEditMode,
                                                             @NotNull Computable<T> computable) {
    ourCommandLineOptions.set(new LightEditCommandLineOptions(shouldWait, lightEditMode));
    try {
      return computable.compute();
    }
    finally {
      ourCommandLineOptions.set(null);
    }
  }

  public static void useCommandLineOptions(boolean shouldWait,
                                           boolean lightEditMode,
                                           @NotNull Disposable disposable) {
    ourCommandLineOptions.set(new LightEditCommandLineOptions(shouldWait, lightEditMode));
    Disposer.register(disposable, new Disposable() {
      @Override
      public void dispose() {
        ourCommandLineOptions.set(null);
      }
    });
  }

  static @Nullable LightEditCommandLineOptions getCommandLineOptions() {
    return ourCommandLineOptions.get();
  }

  static final class LightEditCommandLineOptions {
    private final boolean myShouldWait;
    private final boolean myLightEditMode;

    LightEditCommandLineOptions(boolean shouldWait, boolean lightEditMode) {
      myShouldWait = shouldWait;
      myLightEditMode = lightEditMode;
    }

    public boolean shouldWait() {
      return myShouldWait;
    }
  }
}
