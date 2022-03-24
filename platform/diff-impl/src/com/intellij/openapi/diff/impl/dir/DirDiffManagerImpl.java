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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffManagerImpl extends DirDiffManager {
  private final Project myProject;

  public DirDiffManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public void showDiff(@NotNull final DiffElement dir1,
                       @NotNull final DiffElement dir2,
                       final DirDiffSettings settings,
                       @Nullable final Runnable onWindowClosing) {
    showDiff(settings, new DirDiffTableModel(myProject, dir1, dir2, settings), onWindowClosing);
  }
  
  public void showDiff(final DirDiffSettings settings, DirDiffTableModel model, @Nullable final Runnable onWindowClosing) {
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
      return VirtualFileDiffElement.createElement((VirtualFile)obj);
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
