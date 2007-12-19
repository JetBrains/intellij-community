package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.UIBundle;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;

public class ToggleReadOnlyAttributePanel extends JLabel implements StatusBarPatch {
  public ToggleReadOnlyAttributePanel(StatusBar statusBar) {
    addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {
          processDoubleClick();
        }
      }
    });
    setIconTextGap(0);
    StatusBarTooltipper.install(this, (StatusBarImpl)statusBar);
  }
  public JComponent getComponent() {
    return this;
  }

  private static final Icon myLockedIcon = IconLoader.getIcon("/nodes/lockedSingle.png");
  private static final Icon myUnlockedIcon = myLockedIcon == null ? null : new EmptyIcon(myLockedIcon.getIconWidth(), myLockedIcon.getIconHeight());
  public String updateStatusBar(final Editor selected, final JComponent componentSelected) {
    boolean isWritable = selected == null || selected.getDocument().isWritable();

    setIcon(isWritable ? myUnlockedIcon : myLockedIcon);

    return isReadonlyApplicable()
           ? UIBundle.message("read.only.attr.panel.double.click.to.toggle.attr.tooltip.text") : null;
  }

  public void clear() {
    setIcon(myUnlockedIcon);
  }

  private boolean isReadonlyApplicable() {
    final Project project = getProject();
    if (project == null) return false;
    final FileEditorManager editorManager = FileEditorManager.getInstance(project);
    if (editorManager == null) return false;
    VirtualFile[] selectedFiles = editorManager.getSelectedFiles();
    return isReadOnlyApplicable(selectedFiles);
  }

  private void processDoubleClick() {
    final Project project = getProject();
    if (project == null) {
      return;
    }
    final FileEditorManager editorManager = FileEditorManager.getInstance(project);
    final VirtualFile[] files = editorManager.getSelectedFiles();
    if (!isReadOnlyApplicable(files)) {
      return;
    }
    FileDocumentManager.getInstance().saveAllDocuments();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          ReadOnlyAttributeUtil.setReadOnlyAttribute(files[0], files[0].isWritable());
        }
        catch (IOException e) {
          Messages.showMessageDialog(project, e.getMessage(), UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
        }
      }
    });
  }

  private static boolean isReadOnlyApplicable(final VirtualFile[] files) {
    return files.length > 0 && !files[0].getFileSystem().isReadOnly();
  }

  private Project getProject() {
    return PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this));
  }
}
