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
package com.intellij.ide.scratch;

import com.intellij.lang.Language;
import com.intellij.lang.PerFileMappings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.openapi.wm.impl.status.TextPanel;
import com.intellij.psi.LanguageSubstitutors;
import com.intellij.ui.ClickListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import com.intellij.util.FileContentUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

class ScratchWidget extends EditorBasedWidget implements CustomStatusBarWidget.Multiframe, CustomStatusBarWidget {
  static final String WIDGET_ID = "Scratch";
  private final TextPanel.WithIconAndArrows myPanel = new TextPanel.WithIconAndArrows();

  public ScratchWidget(Project project) {
    super(project);
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        Project project = getProject();
        Editor editor = getEditor();
        final VirtualFile file = getSelectedFile();
        if (project == null || editor == null || file == null) return false;
        showPopup(project, file);

        return true;
      }
    }.installOn(myPanel);
    myConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener.Adapter() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event.getRequestor() == FileContentUtilCore.FORCE_RELOAD_REQUESTOR) {
            update();
            break;
          }
        }
      }
    });
  }

  private void showPopup(Project project, final VirtualFile file) {
    final PerFileMappings<Language> fileService = ScratchFileService.getInstance().getScratchesMapping();
    ListPopup popup = NewScratchFileAction
      .buildLanguageSelectionPopup(project, "Change Language", fileService.getMapping(file), new Consumer<Language>() {
        @Override
        public void consume(Language language) {
          fileService.setMapping(file, language);
          update();
        }
      });
    Dimension dimension = popup.getContent().getPreferredSize();
    Point at = new Point(0, -dimension.height);
    popup.show(new RelativePoint(myPanel, at));
  }

  @NotNull
  @Override
  public String ID() {
    return WIDGET_ID;
  }

  @Nullable
  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  private void update() {
    Project project = getProject();
    if (project == null) return;
    final VirtualFile file = getSelectedFile();
    ScratchFileService fileService = ScratchFileService.getInstance();
    if (file != null && fileService.getRootType(file) instanceof ScratchRootType) {
      Language lang = fileService.getScratchesMapping().getMapping(file);
      if (lang == null) {
        lang = LanguageSubstitutors.INSTANCE.substituteLanguage(((LanguageFileType)file.getFileType()).getLanguage(), file, project);
      }
      myPanel.setText(lang.getDisplayName());
      myPanel.setBorder(WidgetBorder.WIDE);
      myPanel.setIcon(getDefaultIcon(lang));
      myPanel.setVisible(true);
      if (Boolean.TRUE.equals(file.getUserData(NewScratchFileAction.IS_NEW_SCRATCH))) {
        file.putUserData(NewScratchFileAction.IS_NEW_SCRATCH, null);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (myPanel.isVisible()) {
              showPopup(getProject(), file);
            }
          }
        });
      }
    }
    else {
      myPanel.setBorder(null);
      myPanel.setVisible(false);
    }
    if (myStatusBar != null) {
      myStatusBar.updateWidget(WIDGET_ID);
    }
  }

  @Override
  public StatusBarWidget copy() {
    return new ScratchWidget(myProject);
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    update();
    super.fileOpened(source, file);
  }

  @Override
  public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    update();
    super.fileClosed(source, file);
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    update();
    super.selectionChanged(event);
  }

  private static Icon getDefaultIcon(@NotNull Language language) {
    LanguageFileType associatedLanguage = language.getAssociatedFileType();
    return associatedLanguage != null ? associatedLanguage.getIcon() : null;
  }
}
