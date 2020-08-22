// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.completion.EmptyCompletionNotifier;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.lightEdit.intentions.openInProject.LightEditOpenInProjectIntention;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil.OpenPlace.CommandLine;

public final class LightEditUtil {
  private static final String ENABLED_FILE_OPEN_KEY = "light.edit.file.open.enabled";
  private static final String OPEN_FILE_IN_PROJECT_HREF = "open_file_in_project";

  static final Key<String> CREATION_MESSAGE = Key.create("light.edit.file.creation.message");

  private final static Logger LOG = Logger.getInstance(LightEditUtil.class);

  private static boolean ourForceOpenInExistingProjectFlag;

  private LightEditUtil() {
  }

  public static boolean openFile(@NotNull Path path) {
    VirtualFile virtualFile = VfsUtil.findFile(path, true);
    if (virtualFile != null) {
      if (LightEdit.openFile(virtualFile)) {
        LightEditFeatureUsagesUtil.logFileOpen(CommandLine);
        return true;
      }
    }
    else {
      return handleNonExisting(path);
    }
    return false;
  }

  private static boolean handleNonExisting(@NotNull Path path) {
    if (path.getFileName() == null) {
      LOG.error("No file name is given");
    }
    if (path.getNameCount() > 0) {
      String fileName = path.getFileName().toString();
      final Ref<String> creationMessage = Ref.create();
      if (path.getNameCount() > 1) {
        File newFile = path.toFile();
        if (!FileUtil.ensureCanCreateFile(newFile)) {
          creationMessage.set(ApplicationBundle.message("light.edit.file.creation.failed.message", path.toString(), fileName));
        }
      }
      ApplicationManager.getApplication().invokeLater(() -> {
        LightEditorInfo editorInfo = LightEditService.getInstance().createNewDocument(path);
        if (creationMessage.get() != null) {
          editorInfo.getFile().putUserData(CREATION_MESSAGE, creationMessage.get());
          EditorNotifications.getInstance(getProject()).updateNotifications(editorInfo.getFile());
        }
      });
      return true;
    }
    return false;
  }

  public static boolean isOpenInExistingProject() {
    return ourForceOpenInExistingProjectFlag &&
           ProjectManager.getInstance().getOpenProjects().length > 0;
  }

  public static @NotNull Project getProject() {
    return LightEditService.getInstance().getOrCreateProject();
  }

  @Nullable
  public static Project getProjectIfCreated() {
    return LightEditService.getInstance().getProject();
  }

  static boolean confirmClose(@NotNull @NlsContexts.DialogMessage String message,
                              @NotNull @NlsContexts.DialogTitle String title,
                              @NotNull LightEditSaveConfirmationHandler handler) {
    final String[] options = {getCloseSave(), getCloseDiscard(), getCloseCancel()};
    int result = Messages.showDialog(getProject(), message, title, options, 0, Messages.getWarningIcon());
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

  @Nullable
  static VirtualFile chooseTargetFile(@NotNull Component parent, @NotNull LightEditorInfo editorInfo) {
    FileSaverDialog saver =
      FileChooserFactory.getInstance()
        .createSaveFileDialog(new FileSaverDescriptor(
          IdeBundle.message("dialog.title.save.as"),
          IdeBundle.message("label.choose.target.file"),
          getKnownExtensions()),parent);
    VirtualFileWrapper fileWrapper = saver.save(VfsUtil.getUserHomeDir(), editorInfo.getFile().getPresentableName());
    if (fileWrapper != null) {
      return fileWrapper.getVirtualFile(true);
    }
    return null;
  }

  private static String[] getKnownExtensions() {
    return
      ArrayUtil.toStringArray(
        Stream.of(FileTypeManager.getInstance().getRegisteredFileTypes())
          .map(fileType -> fileType.getDefaultExtension()).sorted().distinct().collect(Collectors.toList()));
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
      public void showIncompleteHint(@NotNull Editor editor, @NotNull String text, boolean isDumbMode) {
        HintManager.getInstance().showInformationHint(
          editor,
          StringUtil.escapeXmlEntities(text) + CodeInsightBundle.message("completion.incomplete.light.edit.suffix", OPEN_FILE_IN_PROJECT_HREF),
          e -> {
            if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())
                && OPEN_FILE_IN_PROJECT_HREF.equals(e.getDescription())) {
              VirtualFile file = LightEditService.getInstance().getSelectedFile();
              if (file != null) {
                LightEditOpenInProjectIntention.performOn(file);
              }
            }
          });
      }
    };
  }

  @Nullable
  public static EditorWithProviderComposite findEditorComposite(@NotNull VirtualFile virtualFile) {
    return ((LightEditServiceImpl)LightEditService.getInstance()).getEditPanel().getTabs().findEditorComposite(virtualFile);
  }

  public static void setForceOpenInExistingProject(boolean openInExistingProject) {
    ourForceOpenInExistingProjectFlag = openInExistingProject;
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
}
