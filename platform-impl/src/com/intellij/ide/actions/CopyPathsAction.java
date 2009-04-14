
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.DumbAware;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class CopyPathsAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    final Collection<VirtualFile> files = getFiles(e);
    if (files.isEmpty()) {
      return;
    }
    CopyPasteManager.getInstance().setContents(new StringSelection(getPaths(files)));
  }

  private static String getPaths(Collection<VirtualFile> files) {
    final StringBuilder buf = new StringBuilder(files.size() * 64);
    boolean first = true;
    for (VirtualFile file : files) {
      if (first) first = false;
      else buf.append("\n");
      buf.append(file.getPresentableUrl());
    }
    return buf.toString();
  }

  public void update(AnActionEvent event){
    final Collection<VirtualFile> files = getFiles(event);
    final Presentation presentation = event.getPresentation();
    final boolean enabled = !files.isEmpty();
    presentation.setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      presentation.setVisible(enabled);
    }
    else {
      presentation.setVisible(true);
    }
    presentation.setText((files.size() == 1)
                         ? IdeBundle.message("action.copy.path")
                         : IdeBundle.message("action.copy.paths"));
  }

  @NotNull
  private static Collection<VirtualFile> getFiles(AnActionEvent e) {
    final VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
    if (files == null || files.length == 0) return Collections.emptyList();
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>(files.length);
    for (VirtualFile file : files) {
      if (!(file instanceof LightVirtualFile)) {
        result.add(file);
      }
    }
    return result;
  }

}
