// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.dir;

import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DirDiffModel;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.ide.diff.VirtualFileDiffElement;
import com.intellij.openapi.diff.DirDiffManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public class DirDiffManagerImpl extends DirDiffManager {
  private final Project myProject;

  public DirDiffManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public void showDiff(final @NotNull DiffElement dir1,
                       final @NotNull DiffElement dir2,
                       final DirDiffSettings settings,
                       final @Nullable Runnable onWindowClosing) {
    showDiff(settings, new DirDiffTableModel(myProject, dir1, dir2, settings), onWindowClosing);
  }
  
  public void showDiff(final DirDiffSettings settings, DirDiffTableModel model, final @Nullable Runnable onWindowClosing) {
    if (settings.showInFrame) {
      DirDiffFrame frame = new DirDiffFrame(myProject, model);
      setWindowListener(onWindowClosing, frame.getFrame());
      frame.show();
    } else {
      DirDiffDialog dirDiffDialog = new DirDiffDialog(myProject, model);
      if (myProject == null || myProject.isDefault()/* || isFromModalDialog(myProject)*/) {
        dirDiffDialog.setModal(true);
      }
      setWindowListener(onWindowClosing, dirDiffDialog.getOwner());
      dirDiffDialog.show();
    }
  }

  public static boolean isFromModalDialog(Project project) {
    final Component owner = IdeFocusManager.getInstance(project).getFocusOwner();
    if (owner != null) {
      final DialogWrapper instance = DialogWrapper.findInstance(owner);
      return instance != null && instance.isModal();
    }
    return false;
  }

  private void setWindowListener(final Runnable onWindowClosing, final Window window) {
    if (onWindowClosing != null) {
      window.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {
          onWindowClosing.run();
          window.removeWindowListener(this);
        }
      });
    }
  }

  @Override
  public void showDiff(@NotNull DiffElement dir1, @NotNull DiffElement dir2, DirDiffSettings settings) {
    showDiff(dir1, dir2, settings, null);
  }

  @Override
  public void showDiff(@NotNull DiffElement dir1, @NotNull DiffElement dir2) {
    showDiff(dir1, dir2, new DirDiffSettings());
  }

  @Override
  public boolean canShow(@NotNull DiffElement dir1, @NotNull DiffElement dir2) {
    return dir1.isContainer() && dir2.isContainer();
  }

  @Override
  public DiffElement createDiffElement(Object obj) {
    //TODO make EP
    if (obj instanceof VirtualFile) {
      return VirtualFileDiffElement.createElement((VirtualFile)obj, (VirtualFile)obj);
    }
    return null;
  }

  @Override
  public DirDiffModel createDiffModel(DiffElement e1, DiffElement e2, DirDiffSettings settings) {
    DirDiffTableModel newModel = new DirDiffTableModel(myProject, e1, e2, settings);
    newModel.reloadModelSynchronously();
    return newModel;
  }
}
